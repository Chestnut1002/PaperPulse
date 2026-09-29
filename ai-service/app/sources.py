"""论文检索的多数据源实现与降级链。

本机网络实测:`export.arxiv.org`(arXiv 官方 API)不可达,所以检索不走 arXiv;
**Semantic Scholar 为主**(索引了 arXiv,带引用数与 venue),**Crossref 为降级备选**(最稳定)。
两者返回的数据会被归一化成同一个形状,上层不必关心结果来自哪里。

同一份逻辑原先写在 `scripts/search_papers.py` 里当调试脚本,现在收进服务 ——
调试脚本改为调用这里,避免两处各维护一份检索代码。
"""

import html
import re
import time
from dataclasses import dataclass
from typing import Callable

import httpx

from . import config

SEMANTIC_SCHOLAR_API = "https://api.semanticscholar.org/graph/v1/paper/search"
CROSSREF_API = "https://api.crossref.org/works"

# 每次检索最多重试几次(仅针对限流)
_RETRIES = 2
_RETRY_WAIT_SECONDS = 2


class AllSourcesFailed(RuntimeError):
    """所有数据源都没能返回结果。"""


@dataclass(frozen=True)
class Source:
    """一个数据源。`key` 必须与 Java `PaperSource.key()` 一致(小写枚举名)。"""

    key: str
    label: str
    search: Callable[..., list[dict]]


@dataclass(frozen=True)
class SearchOutcome:
    """检索结果 + 实际命中的是哪个源(降级链走到哪就是谁)。"""

    source_label: str
    papers: list[dict]


# ---------------------------------------------------------------- 公共小工具


def _headers() -> dict:
    """请求头。S2 配了 key 就带上 —— 匿名共享池经常 429,带 key 走独立配额。"""
    headers = {"User-Agent": config.USER_AGENT}
    if config.SEMANTIC_SCHOLAR_API_KEY:
        headers["x-api-key"] = config.SEMANTIC_SCHOLAR_API_KEY
    return headers


def _fetch(url: str, params: dict, source_label: str) -> dict:
    """发一次 GET 并返回 JSON。被限流就等一会儿重试,其它错误直接抛。"""
    last_error = ""
    for attempt in range(_RETRIES):
        try:
            response = httpx.get(
                url,
                params=params,
                timeout=config.REQUEST_TIMEOUT,
                headers=_headers(),
            )
        except httpx.HTTPError as exc:
            last_error = f"连接失败:{exc}"
            continue

        if response.status_code == 200:
            return response.json()

        last_error = f"HTTP {response.status_code}"
        if response.status_code == 429 and attempt < _RETRIES - 1:
            time.sleep(_RETRY_WAIT_SECONDS)
            continue
        raise RuntimeError(f"{source_label} 返回 {last_error}")

    raise RuntimeError(f"{source_label} 重试后仍然失败:{last_error}")


def _clean_abstract(raw: str | None) -> str | None:
    """把摘要里的 HTML / XML 标签清成纯文本(Crossref 的摘要是 JATS 格式)。"""
    if not raw:
        return None
    text = html.unescape(html.unescape(raw))  # 有的记录被转义了两层(&lt;p&gt;)
    text = re.sub(r"<[^>]+>", " ", text)  # 去掉 <jats:p> 之类的标签
    text = re.sub(r"\s+", " ", text).strip()
    return text or None


def _non_empty(value: object) -> str | None:
    """把空字符串、空列表都归一成 None —— 空串进库比 null 更麻烦。

    Crossref 的 `title` / `container-title` 是数组,取**第一个非空元素**而不是 `[0]`:
    后者遇到 `["", "真实标题"]` 会把有值误判成没值。
    """
    if isinstance(value, list):
        for item in value:
            text = str(item).strip()
            if text:
                return text
        return None
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _in_year_range(year: int | None, year_from: int | None, year_to: int | None) -> bool:
    """年份过滤。**年份缺失时视为不符合** —— 用户明确要"2024 年以后"时,
    塞进来一篇年份不详的论文是在违背他的约束。两个 API 的年份过滤都是粗筛,这里是细筛。"""
    if year_from is None and year_to is None:
        return True
    if year is None:
        return False
    if year_from is not None and year < year_from:
        return False
    if year_to is not None and year > year_to:
        return False
    return True


def _dedupe(papers: list[dict]) -> list[dict]:
    """按 externalId 去重,保留先出现的(顺序即相关度顺序)。"""
    seen: set[str] = set()
    unique = []
    for paper in papers:
        external_id = paper["externalId"]
        if external_id in seen:
            continue
        seen.add(external_id)
        unique.append(paper)
    return unique


_DOI_PREFIXES = ("https://doi.org/", "http://doi.org/", "doi:")


def normalize_doi(raw: object) -> str | None:
    """规范化 DOI。

    DOI 本身大小写不敏感,而且各家返回的形式不一(裸串、带 https://doi.org/ 前缀)。
    不做归一的话,同一篇论文会因为写法不同被当成两个 —— 而这个字段正是用来跨源认人的。
    """
    text = _non_empty(raw)
    if text is None:
        return None

    lowered = text.strip().lower()
    for prefix in _DOI_PREFIXES:
        if lowered.startswith(prefix):
            lowered = lowered[len(prefix):]
            break

    # 只接受 10.xxxx/... 的形状;不符合的宁可不认,也别拿一个假身份去合并两篇不同的论文
    return lowered if lowered.startswith("10.") and "/" in lowered else None


# ---------------------------------------------------------------- 各数据源


def semantic_scholar_to_paper(item: dict) -> dict | None:
    """把 S2 的一条记录映射成统一的论文形状;缺关键字段返回 None。

    抽成独立函数是为了能脱离网络单测 —— 映射规则(尤其是 DOI 从哪来)是这里最容易错的地方。
    """
    external_id = _non_empty(item.get("paperId"))
    if external_id is None:
        return None  # 没有 ID 就没法落库去重

    return {
        "source": "semantic_scholar",
        "externalId": external_id,
        "title": _non_empty(item.get("title")) or "(无标题)",
        "authors": [
            name
            for author in (item.get("authors") or [])
            if (name := _non_empty(author.get("name")))
        ],
        "abstractText": _non_empty(item.get("abstract")),
        "publicationYear": item.get("year"),
        "venue": _non_empty(item.get("venue")),
        "url": _non_empty(item.get("url")),
        # S2 把 DOI 放在 externalIds 里,不在顶层
        "doi": normalize_doi((item.get("externalIds") or {}).get("DOI")),
        "citationCount": item.get("citationCount") or 0,
    }


def crossref_to_paper(item: dict) -> dict | None:
    """把 Crossref 的一条记录映射成统一的论文形状;没有 DOI 返回 None。

    Crossref 的身份就是 DOI,没有它既没法落库也没法跨源认人。
    """
    doi = normalize_doi(item.get("DOI"))
    if doi is None:
        return None

    date_parts = (item.get("issued") or {}).get("date-parts") or [[None]]
    return {
        "source": "crossref",
        "externalId": doi,
        "title": _non_empty(item.get("title")) or "(无标题)",
        "authors": [
            name
            for author in (item.get("author") or [])
            if (name := _non_empty(f"{author.get('given', '')} {author.get('family', '')}"))
        ],
        "abstractText": _clean_abstract(item.get("abstract")),
        "publicationYear": date_parts[0][0],
        "venue": _non_empty(item.get("container-title")),
        "url": _non_empty(item.get("URL")),
        "doi": doi,
        "citationCount": item.get("is-referenced-by-count") or 0,
    }


def _collect(mapped: list[dict | None], year_from: int | None, year_to: int | None) -> list[dict]:
    """丢掉映射失败(null)与不符合年份约束的条目。"""
    return [
        paper
        for paper in mapped
        if paper is not None and _in_year_range(paper["publicationYear"], year_from, year_to)
    ]


def search_semantic_scholar(keywords: str, limit: int,
                            year_from: int | None, year_to: int | None) -> list[dict]:
    params: dict[str, object] = {
        "query": keywords,
        "limit": limit,
        # externalIds 里带 DOI —— 它是跨源认人的依据,少了它同一篇论文会被存成两行
        "fields": "paperId,title,year,authors,abstract,url,citationCount,venue,externalIds",
    }
    if year_from is not None or year_to is not None:
        # S2 的年份写法是 "2015-2020" / "2015-" / "-2020"
        params["year"] = f"{year_from or ''}-{year_to or ''}"

    data = _fetch(SEMANTIC_SCHOLAR_API, params, "Semantic Scholar")
    return _collect([semantic_scholar_to_paper(item) for item in (data.get("data") or [])],
                    year_from, year_to)


def search_crossref(keywords: str, limit: int,
                    year_from: int | None, year_to: int | None) -> list[dict]:
    params: dict[str, object] = {
        "query.bibliographic": keywords,
        "rows": limit,
        "select": "DOI,title,author,issued,is-referenced-by-count,abstract,URL,container-title",
    }
    date_filters = []
    if year_from is not None:
        date_filters.append(f"from-pub-date:{year_from}-01-01")
    if year_to is not None:
        date_filters.append(f"until-pub-date:{year_to}-12-31")
    if date_filters:
        params["filter"] = ",".join(date_filters)

    data = _fetch(CROSSREF_API, params, "Crossref")
    return _collect([crossref_to_paper(item) for item in data.get("message", {}).get("items", [])],
                    year_from, year_to)


SOURCES: list[Source] = [
    Source("semantic_scholar", "Semantic Scholar", search_semantic_scholar),
    Source("crossref", "Crossref", search_crossref),
]


# ---------------------------------------------------------------- 对外接口


def search_papers(keywords: str, limit: int = config.DEFAULT_LIMIT,
                  year_from: int | None = None, year_to: int | None = None) -> SearchOutcome:
    """按关键词检索论文。依次尝试各个数据源,谁先给出结果就用谁。"""
    errors = []
    for source in SOURCES:
        try:
            papers = source.search(keywords, limit, year_from, year_to)
        except RuntimeError as exc:
            errors.append(f"{source.label}:{exc}")
            continue

        if papers:
            return SearchOutcome(source.label, _dedupe(papers))
        errors.append(f"{source.label}:无结果")

    raise AllSourcesFailed("所有数据源都没能返回结果 → " + " | ".join(errors))
