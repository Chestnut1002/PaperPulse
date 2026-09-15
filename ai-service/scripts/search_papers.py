"""search_papers.py —— 给程序装上"查论文"的工具(多数据源自动降级)。

数据源:
  Semantic Scholar(索引了 arXiv,有引用数)—— 免费无需 key,但会限流
  Crossref(全球文献注册中心,DOI 权威)  —— 免费无需 key,较稳定
  S2 失败时自动改用 Crossref;两者返回的数据会被"归一化"成同样的格式,
  所以上层的 format_paper 完全不用关心数据是从哪来的。

注意:arXiv 官方 API(export.arxiv.org)在本机网络不可达,检索不走它;
     arXiv 主站 arxiv.org 可访问,后续取全文用得上。
     OpenAlex 本机同样返回 429,暂未接入,将来网络恢复可作为第三备胎。

运行(在项目根目录):
    ai-service\\.venv\\Scripts\\python.exe -X utf8 ai-service\\scripts\\search_papers.py "contrastive learning recommendation"
"""
import html
import re
import sys
import time

import httpx

USER_AGENT = "PaperPulse/0.1 (mailto:paperpulse@example.com)"  # 带上项目标识,是学术 API 的礼貌
S2_API = "https://api.semanticscholar.org/graph/v1/paper/search"
CROSSREF_API = "https://api.crossref.org/works"


# ---------------------------------------------------------------- 公共小工具

def _fetch(url: str, params: dict, source: str, retries: int = 2) -> dict:
    """发一次 GET 请求并返回 JSON;被限流就等 2 秒重试,其它错误直接抛异常。"""
    for attempt in range(retries):
        response = httpx.get(url, params=params, timeout=25, headers={"User-Agent": USER_AGENT})
        if response.status_code == 200:
            return response.json()
        if response.status_code == 429 and attempt < retries - 1:
            print(f"    [{source} 限流,2 秒后重试...]")
            time.sleep(2)
            continue
        raise RuntimeError(f"{source} 返回 HTTP {response.status_code}")
    raise RuntimeError(f"{source} 重试后仍然失败")


def _clean_abstract(raw: str | None) -> str:
    """把摘要里的 HTML/XML 标签清理成纯文本(Crossref 的摘要是 JATS 格式)。"""
    if not raw:
        return ""
    text = html.unescape(html.unescape(raw))  # 有的记录被转义了两层(&lt;p&gt;)
    text = re.sub(r"<[^>]+>", " ", text)      # 去掉 <jats:p> 之类的标签
    return re.sub(r"\s+", " ", text).strip()


# ---------------------------------------------------------------- 各数据源

def search_semantic_scholar(query: str, limit: int) -> list[dict]:
    data = _fetch(
        S2_API,
        {"query": query, "limit": limit, "fields": "title,year,authors,abstract,url,citationCount"},
        "Semantic Scholar",
    )
    return [
        {
            "title": paper.get("title") or "(无标题)",
            "year": paper.get("year"),
            "authors": [author["name"] for author in (paper.get("authors") or [])],
            "abstract": paper.get("abstract") or "",
            "url": paper.get("url") or "",
            "citationCount": paper.get("citationCount") or 0,
        }
        for paper in data.get("data", [])
    ]


def search_crossref(query: str, limit: int) -> list[dict]:
    data = _fetch(
        CROSSREF_API,
        {
            "query.bibliographic": query,
            "rows": limit,
            "select": "title,author,issued,DOI,is-referenced-by-count,abstract,URL",
        },
        "Crossref",
    )
    papers = []
    for item in data["message"]["items"]:
        date_parts = (item.get("issued") or {}).get("date-parts") or [[None]]
        papers.append(
            {
                "title": (item.get("title") or ["(无标题)"])[0],
                "year": date_parts[0][0],
                "authors": [
                    f"{a.get('given', '')} {a.get('family', '')}".strip()
                    for a in (item.get("author") or [])
                ],
                "abstract": _clean_abstract(item.get("abstract")),
                "url": item.get("URL") or "",
                "citationCount": item.get("is-referenced-by-count") or 0,
            }
        )
    return papers


SOURCES = [
    ("Semantic Scholar", search_semantic_scholar),
    ("Crossref", search_crossref),
]


# ---------------------------------------------------------------- 对外接口

def search_papers(query: str, limit: int = 5) -> list[dict]:
    """按关键词检索论文,返回列表;每个元素是一篇论文(字典)。

    依次尝试各个数据源,谁先成功就用谁 —— 这就是"降级"(fallback)。
    """
    errors = []
    for name, search in SOURCES:
        print(f"  尝试 {name} ...")
        try:
            papers = search(query, limit)
        except RuntimeError as exc:
            print(f"    ✗ {exc}")
            errors.append(f"{name}: {exc}")
            continue
        if papers:
            print(f"    ✓ 拿到 {len(papers)} 篇")
            return papers
        print("    ✗ 没有找到结果")
        errors.append(f"{name}: 无结果")
    raise RuntimeError("所有数据源都失败 → " + " | ".join(errors))


def format_paper(index: int, paper: dict) -> str:
    """把一篇论文格式化成一屏可读的文本。"""
    authors = ", ".join(paper["authors"][:3]) or "(未提供)"
    abstract = paper["abstract"][:150] or "(无摘要)"
    return (
        f"{index}. {paper['title']}  ({paper.get('year') or '?'})\n"
        f"   作者: {authors}\n"
        f"   引用数: {paper['citationCount']}\n"
        f"   链接: {paper['url']}\n"
        f"   摘要: {abstract}...\n"
    )


if __name__ == "__main__":
    # sys.argv 是"命令行参数"列表:argv[0] 是脚本名,argv[1] 才是你输入的检索词
    query = sys.argv[1] if len(sys.argv) > 1 else "recommendation system"
    print(f"检索:{query}")
    for i, item in enumerate(search_papers(query), start=1):
        print(format_paper(i, item))
