"""论文检索的多数据源实现与降级链。

三个源并发:**Semantic Scholar**(索引了 arXiv,带引用数与 venue)、**Crossref**(最稳定)、
**arXiv 官方 API**。三者的返回会被归一化成同一个形状,上层不必关心结果来自哪里;
跨源重复由合并层处理。

(这里原先写着"`export.arxiv.org` 不可达,所以检索不走 arXiv" —— 那是当时的网络状况,
后来通了就接进来了。**可达性会变,别凭记忆下结论,先实测。**)

同一份逻辑原先写在 `scripts/search_papers.py` 里当调试脚本,现在收进服务 ——
调试脚本改为调用这里,避免两处各维护一份检索代码。
"""

import html
import json
import re
import time
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from typing import Callable

import httpx

from . import config

SEMANTIC_SCHOLAR_API = "https://api.semanticscholar.org/graph/v1/paper/search"
CROSSREF_API = "https://api.crossref.org/works"
ARXIV_API = "https://export.arxiv.org/api/query"

# arXiv 返回的是 Atom XML,元素都带命名空间
ATOM = "{http://www.w3.org/2005/Atom}"
ARXIV_NS = "{http://arxiv.org/schemas/atom}"

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
    """检索结果 + 实际贡献了结果的源。

    `source_label` 可能是多个源的组合(如 `arXiv、Crossref`)—— 现在会同时查所有源再合并,
    不像以前"谁先返回结果就只用谁"。
    """

    source_label: str
    papers: list[dict]


# ---------------------------------------------------------------- 公共小工具


def _headers() -> dict:
    """请求头。S2 配了 key 就带上 —— 匿名共享池经常 429,带 key 走独立配额。"""
    headers = {"User-Agent": config.USER_AGENT}
    if config.SEMANTIC_SCHOLAR_API_KEY:
        headers["x-api-key"] = config.SEMANTIC_SCHOLAR_API_KEY
    return headers


def _fetch_text(url: str, params: dict, source_label: str) -> str:
    """发一次 GET 并返回原始文本。被限流就等一会儿重试,其它错误直接抛。

    返回文本而不是解析好的对象:各源的响应格式不一样(JSON / Atom XML),
    解析留给各自的映射函数,这里只管"把字节拿回来"。
    """
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
            return response.text

        last_error = f"HTTP {response.status_code}"
        if response.status_code == 429 and attempt < _RETRIES - 1:
            time.sleep(_RETRY_WAIT_SECONDS)
            continue
        raise RuntimeError(f"{source_label} 返回 {last_error}")

    raise RuntimeError(f"{source_label} 重试后仍然失败:{last_error}")


def _fetch(url: str, params: dict, source_label: str) -> dict:
    """发一次 GET 并把响应解析成 JSON。"""
    return json.loads(_fetch_text(url, params, source_label))


def _clean_text(raw: str | None) -> str | None:
    """还原 HTML 转义、去掉标签、压缩空白。

    **标题也要过这一道**:Crossref 的标题里常见 `&amp;`(实测「Explainable Large Language
    Models &amp; iContracts」),不还原就会原样显示在界面上。摘要则是 JATS 格式,带 `<jats:p>` 之类的标签。
    """
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
        "title": _clean_text(item.get("title")) or "(无标题)",
        "authors": [
            name
            for author in (item.get("authors") or [])
            if (name := _non_empty(author.get("name")))
        ],
        "abstractText": _clean_text(item.get("abstract")),
        "publicationYear": item.get("year"),
        "venue": _clean_text(item.get("venue")),
        "url": _non_empty(item.get("url")),
        # S2 把 DOI 放在 externalIds 里,不在顶层
        "doi": normalize_doi((item.get("externalIds") or {}).get("DOI")),
        # S2 索引了 arXiv,所以不少记录带 arXiv 编号 —— 有它这篇就能精读
        "arxivId": _non_empty((item.get("externalIds") or {}).get("ArXiv")),
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
        "title": _clean_text(_non_empty(item.get("title"))) or "(无标题)",
        "authors": [
            name
            for author in (item.get("author") or [])
            if (name := _non_empty(f"{author.get('given', '')} {author.get('family', '')}"))
        ],
        "abstractText": _clean_text(item.get("abstract")),
        "publicationYear": date_parts[0][0],
        "venue": _clean_text(_non_empty(item.get("container-title"))),
        "url": _non_empty(item.get("URL")),
        "doi": doi,
        # arXiv 预印本在 Crossref 里的 DOI 就是 10.48550/arxiv.XXXX —— 从它反推编号,
        # 这样一部分 Crossref 论文也能精读
        "arxivId": _arxiv_from_doi(doi),
        "citationCount": item.get("is-referenced-by-count") or 0,
    }


# arXiv 的 DOI 前缀。Crossref 里预印本的 DOI 长这样,正式发表版则是期刊的 DOI。
_ARXIV_DOI_PREFIX = "10.48550/arxiv."


def _arxiv_from_doi(doi: str | None) -> str | None:
    """从 arXiv 的 DOI 反推编号;不是 arXiv 的 DOI 就返回 None。"""
    if doi and doi.startswith(_ARXIV_DOI_PREFIX):
        return doi[len(_ARXIV_DOI_PREFIX):] or None
    return None


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


def _parse_atom_entries(text: str) -> list[ET.Element]:
    """把 arXiv 返回的 Atom 文本解析成条目列表。

    抽出来是因为**标题反查与关键词检索是两条路、同一段解析**:
    `id_list` 那条路(按编号取)也用同一份,免得三处各写一遍 try/except。
    """
    try:
        root = ET.fromstring(text)
    except ET.ParseError as exc:
        raise RuntimeError(f"arXiv 返回的不是合法 XML:{exc}") from exc
    return root.findall(f"{ATOM}entry")


def _arxiv_entries(query: str, limit: int) -> list[ET.Element]:
    """按 arXiv 的查询语法取回条目。两条标题查询路径的差异只在 query 怎么拼。"""
    return _parse_atom_entries(_fetch_text(
        ARXIV_API,
        {"search_query": query, "max_results": limit, "sortBy": "relevance"},
        "arXiv",
    ))


def _strip_arxiv_version(arxiv_id: str) -> str:
    """去掉版本后缀。

    同一篇论文的 v1 / v2 是**同一篇**,带着版本号落库会把每次修订存成新的一行 ——
    而 arXiv 上修版很常见。
    """
    return re.sub(r"v\d+$", "", arxiv_id.strip())


def arxiv_to_paper(entry: ET.Element) -> dict | None:
    """把 arXiv 的一条 Atom 条目映射成统一的论文形状。

    arXiv 没有引用数,也没有"会议/期刊"字段(只有作者自愿登记的 `journal_ref`),
    所以那两项留空 —— 缺字段是不可接受的?不,是**如实反映**:它确实不提供。
    """
    raw_id = _non_empty(entry.findtext(f"{ATOM}id"))
    if raw_id is None:
        return None

    # <id> 是个 URL,形如 http://arxiv.org/abs/2502.19271v1
    arxiv_id = _strip_arxiv_version(raw_id.rsplit("/abs/", 1)[-1])
    if not arxiv_id:
        return None

    published = _non_empty(entry.findtext(f"{ATOM}published")) or ""
    year = int(published[:4]) if published[:4].isdigit() else None

    return {
        "source": "arxiv",
        "externalId": arxiv_id,
        "title": _clean_text(entry.findtext(f"{ATOM}title")) or "(无标题)",
        "authors": [
            name
            for author in entry.findall(f"{ATOM}author")
            if (name := _clean_text(author.findtext(f"{ATOM}name")))
        ],
        "abstractText": _clean_text(entry.findtext(f"{ATOM}summary")),
        "publicationYear": year,
        "venue": _clean_text(entry.findtext(f"{ARXIV_NS}journal_ref")),
        # 作者自愿登记的正式发表 DOI,实测只有约一成的论文有 —— 有就用,没有就算了
        "doi": normalize_doi(entry.findtext(f"{ARXIV_NS}doi")),
        # 它自己就是 arXiv 来源,编号必然有 —— 这篇一定能精读
        "arxivId": arxiv_id,
        "url": raw_id,
        "citationCount": 0,
    }


def search_arxiv(keywords: str, limit: int,
                 year_from: int | None, year_to: int | None) -> list[dict]:
    # 关键词**不加引号**:加了会变成精确短语匹配(实测「contrastive learning
    # recommender systems」整句加引号命中 0 条),不加则是各词 AND。
    query = f"all:{keywords}"
    if year_from is not None or year_to is not None:
        start = f"{year_from or 1991}01010000"
        end = f"{year_to or 2999}12312359"
        query = f"{query} AND submittedDate:[{start} TO {end}]"

    return _collect([arxiv_to_paper(entry) for entry in _arxiv_entries(query, limit)],
                    year_from, year_to)


def fetch_arxiv_by_id(arxiv_id: str) -> dict | None:
    """按编号取一篇 arXiv 论文的元数据;取不到返回 None。

    <p>用在"直接粘贴 arXiv 编号精读"那条路上 —— 用户手里有编号,不必先搜一遍。

    <p><b>要防一手 arXiv 的"错误条目"</b>:编号不存在时它照样返回 200,条目里放的是一篇
    标题为 `Error` 的假论文。不识别它的话,用户会得到一篇叫 "Error" 的论文。
    """
    text = _fetch_text(ARXIV_API, {"id_list": arxiv_id, "max_results": 1}, "arXiv")
    entries = _parse_atom_entries(text)
    if not entries:
        return None

    entry = entries[0]
    # arXiv 的"没找到"不是报错,而是给一个标题为 Error 的条目
    if _clean_text(entry.findtext(f"{ATOM}title")) == "Error":
        return None
    return arxiv_to_paper(entry)


# ------------------------------------------------------- 按标题反查(REQ-003 P3)

# 标题反查最多取回多少候选。多了没用 —— "认不认"由 Java 侧的匹配规则逐个过,
# 而每多一条就多一分把噪声当证据的机会。
_TITLE_LOOKUP_LIMIT = 8

# 退化查询最多保留几个显著词。AND 越多越精确、也越容易因一处措辞差异而全落空。
_TITLE_QUERY_MAX_WORDS = 8

# 标题里这些字符是 arXiv 查询语法的一部分(& | ( ) " 等)。
# **实测非得剔掉不可**:arXiv 遇到它们不报错,而是**静默改写成另一个查询** ——
# `ti:a & b` 会变成 `ti:a OR all:b`,搜出来的是完全不相干的东西。
_QUERY_UNSAFE = re.compile(r"[^\w \-.,:]+")

# 退化查询里丢掉的词:英文虚词。**只丢虚词,不丢内容词** ——
# 内容词即使很常见(如 learning),丢多了会让查询失去区分度、把正确的那篇挤出候选。
_STOPWORDS = frozenset("""
a an the and or of for with without from into over under between through via using
based towards toward on in at to by is are was were be been this that these those
""".split())


def _clean_query_text(title: str) -> str:
    """把标题清理成能安全拼进 arXiv 查询的样子。规则见 {@link _QUERY_UNSAFE}。"""
    text = html.unescape(title or "")
    text = re.sub(r"<[^>]+>", " ", text)  # 万一还带着标签(历史数据)
    text = _QUERY_UNSAFE.sub(" ", text)
    return re.sub(r"\s+", " ", text).strip()


def _title_phrase_query(title: str) -> str | None:
    """精确短语查询。**最准,但措辞差一个内容词就 0 条**(实测)。"""
    cleaned = _clean_query_text(title)
    return f'ti:"{cleaned}"' if cleaned else None


def _title_words_query(title: str) -> str | None:
    """退化查询:显著词逐个 AND。实测措辞有出入时它还能找到。

    超过上限时保留**最长的几个词**(长词更可能有区分度),再按原顺序拼回去 ——
    查询的顺序不影响结果,但拼得像人写的更好读日志。
    """
    words = [word for word in _clean_query_text(title).split()
             if len(word) >= 3 and word.lower() not in _STOPWORDS]
    if not words:
        return None

    if len(words) > _TITLE_QUERY_MAX_WORDS:
        longest = sorted(range(len(words)), key=lambda index: len(words[index]),
                         reverse=True)[:_TITLE_QUERY_MAX_WORDS]
        words = [words[index] for index in sorted(longest)]

    return " AND ".join(f"ti:{word}" for word in words)


def search_arxiv_by_title(title: str, limit: int = _TITLE_LOOKUP_LIMIT) -> list[dict]:
    """按标题找 arXiv 上的预印本;找不到返回空列表。

    <p>用在"库里已有这篇论文(多半来自 Crossref),但它没有 arXiv 编号、
    所以精读不了"那条路上 —— 拿标题去 arXiv 找它的预印本,找到就能补上编号。

    <p>**这里只负责"捞",不负责"认"。** 判"是不是同一篇"的规则复用 Java 侧的
    {@code PaperMatcher}(标题 + 作者 + 年份),不在这里重写第二套归一化 ——
    两处各写一套迟早会不一致,而认错的代价是把用户领到**另一篇论文**上。

    <p>两步:先精确短语,再退化为显著词 AND。两次都空 = 没找到,
    是**正常结果**,不是异常(调用方按空列表处理)。
    """
    for query in (_title_phrase_query(title), _title_words_query(title)):
        if not query:
            continue

        papers = _collect(
            [arxiv_to_paper(entry) for entry in _arxiv_entries(query, limit)], None, None)
        if papers:
            return papers

    return []


SOURCES: list[Source] = [
    # 顺序 = 结果列表里的交织顺序。按"元数据丰富度"排,失败的自然被跳过,
    # 所以 S2 限流时 arXiv 会顶到最前面,不需要另写降级逻辑。
    Source("semantic_scholar", "Semantic Scholar", search_semantic_scholar),
    Source("arxiv", "arXiv", search_arxiv),
    Source("crossref", "Crossref", search_crossref),
]


# ---------------------------------------------------------------- 对外接口


def _interleave(outcomes: list[tuple[str, list[dict]]],
                limit: int) -> tuple[list[str], list[dict]]:
    """把各源的结果按轮转交织,并按身份去重。

    <p>**为什么用轮转而不是按相关度排序**:各源的分数**量纲完全不同** ——
    S2 给 0~1 的相关性,Crossref 给一个无上界的 score,arXiv 只给顺序没有分数。
    跨源比较这些数字没有意义。轮转保留各源自己的顺序,也不需要归一化。

    <p>去重做两遍:同一 `(来源, 外部 ID)` 不重复;同一 DOI 不重复(跨源的同一篇论文)。
    **只做精确匹配** —— 不按标题猜,猜错会把两篇不同的论文合并掉。
    """
    queues = [list(papers) for _, papers in outcomes]

    merged: list[dict] = []
    used_labels: list[str] = []
    seen_external: set[tuple[str, str]] = set()
    seen_doi: set[str] = set()

    while len(merged) < limit and any(queues):
        for index, queue in enumerate(queues):
            if not queue:
                continue

            paper = queue.pop(0)
            key = (paper["source"], paper["externalId"])
            doi = paper.get("doi")
            if key in seen_external or (doi is not None and doi in seen_doi):
                continue

            seen_external.add(key)
            if doi is not None:
                seen_doi.add(doi)
            merged.append(paper)

            label = outcomes[index][0]
            if label not in used_labels:
                used_labels.append(label)

            if len(merged) >= limit:
                break

    return used_labels, merged


def search_papers(keywords: str, limit: int = config.DEFAULT_LIMIT,
                  year_from: int | None = None, year_to: int | None = None) -> SearchOutcome:
    """**并发**查所有数据源,再把结果合并。

    <p>不再"谁先返回结果就用谁":各个源各有所长 —— S2 有引用数、arXiv 有最新的预印本、
    Crossref 最稳且 DOI 齐全 —— 只取一个等于主动丢掉另外两个的覆盖。

    <p>串行查三个源最坏要等 75 秒(各自超时 25 秒),所以并发。
    某个源挂了不影响其余,它只是不出现在结果里。
    """
    with ThreadPoolExecutor(max_workers=len(SOURCES)) as pool:
        # 按 SOURCES 的顺序提交与收集,保证交织的顺序是确定的
        futures = [(source, pool.submit(source.search, keywords, limit, year_from, year_to))
                   for source in SOURCES]

        outcomes: list[tuple[str, list[dict]]] = []
        errors: list[str] = []
        for source, future in futures:
            try:
                papers = future.result()
            except RuntimeError as exc:
                errors.append(f"{source.label}:{exc}")
                continue
            if papers:
                outcomes.append((source.label, papers))

    if not outcomes:
        raise AllSourcesFailed("所有数据源都没能返回结果 → " + " | ".join(errors))

    labels, papers = _interleave(outcomes, limit)
    return SearchOutcome("、".join(labels), papers)


def _candidate_key(paper: dict) -> str:
    """候选的身份键。

    有 DOI 用 DOI —— 同一篇论文从不同源、经由不同检索词捞上来时,DOI 是唯一认得出它的东西。
    没有 DOI 就退回 `(来源, 外部 ID)`。
    """
    doi = paper.get("doi")
    return f"doi:{doi}" if doi else f"id:{paper['source']}:{paper['externalId']}"


def find_candidates(queries: list[tuple[str, str]],
                    per_query: int) -> tuple[list[str], list[dict]]:
    """按若干 `(标签, 检索词)` 取推荐候选,再按论文合并。

    <p>与 {@link search_papers} 的区别:这里没有"用户的自然语言",只有既定的检索词,
    所以不需要也不该走大模型拆解。

    <p>同一篇被多路检索命中时把标签**累积**起来 —— 调用方靠它判断"最相关的是哪个兴趣",
    以及据此生成推荐理由。

    <p>各路**并发**发起;某一路失败不影响其余 —— 少一路候选,总好过整页推荐打不开。
    """
    if not queries:
        return [], []

    with ThreadPoolExecutor(max_workers=len(queries)) as pool:
        futures = [(tag, pool.submit(search_papers, keywords, per_query, None, None))
                   for tag, keywords in queries]

        merged: dict[str, dict] = {}
        order: list[str] = []
        labels: list[str] = []
        succeeded = 0

        for tag, future in futures:
            try:
                outcome = future.result()
            except RuntimeError:
                continue

            succeeded += 1
            for label in outcome.source_label.split("、"):
                if label and label not in labels:
                    labels.append(label)

            for paper in outcome.papers:
                key = _candidate_key(paper)
                existing = merged.get(key)
                if existing is not None:
                    if tag not in existing["matchedTags"]:
                        existing["matchedTags"].append(tag)
                    continue

                entry = dict(paper)
                entry["matchedTags"] = [tag]
                merged[key] = entry
                order.append(key)

    if succeeded == 0:
        # 每一路都失败了 —— 那是上游不可用,不是"没有候选",两者要分得开
        raise AllSourcesFailed("所有检索路都失败了,无法生成候选")

    return labels, [merged[key] for key in order]
