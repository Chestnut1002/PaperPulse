"""对外接口的数据形状。

字段名刻意用 camelCase 而不是 Python 惯用的 snake_case:这些是**跨语言的线上契约**,
Java 侧用 Jackson 直接反序列化。保持同名可以让两边都不写映射代码 —— 少一层映射,
就少一处"改了这边忘了改那边"。
"""

from pydantic import BaseModel, Field

from . import config


class SearchRequest(BaseModel):
    """一次检索请求。"""

    query: str = Field(min_length=2, max_length=500, description="自然语言检索需求")
    limit: int = Field(default=config.DEFAULT_LIMIT, ge=1, le=config.MAX_LIMIT)


class SearchPlan(BaseModel):
    """Agent 把自然语言拆解成的检索策略。单独返回是为了让"为什么这样搜"对用户可见。"""

    keywords: str = Field(description="实际用于检索的英文关键词")
    yearFrom: int | None = Field(default=None, description="起始年份,不限则为 null")
    yearTo: int | None = Field(default=None, description="结束年份,不限则为 null")
    rationale: str = Field(default="", description="拆解理由,用中文说给用户看")


class Paper(BaseModel):
    """一篇检索到的论文。

    字段与 Java 侧的 `PaperInput` 一一对应,后端拿到后可直接落库。
    `source` 的取值必须与 Java `PaperSource.key()` 一致(小写枚举名)。
    """

    source: str
    externalId: str
    title: str
    authors: list[str] = Field(default_factory=list)
    abstractText: str | None = None
    publicationYear: int | None = None
    venue: str | None = None
    url: str | None = None
    # DOI:同一篇论文在不同来源下的 DOI 相同,而后端是按 (来源, 外部 ID) 去重的,
    # 所以只靠那对键会把同一篇论文在两个源里各存一行。带上 DOI,后端才能跨源认出它。
    doi: str | None = None
    # arXiv 编号 —— 有它才谈得上精读(全文是从 arXiv 抓的)。
    # S2 的记录里带(Semantic Scholar 索引了 arXiv);Crossref 那边则要看 DOI 是不是 arXiv 的。
    arxivId: str | None = None

    # 以下两个只有展示用途,不参与落库
    citationCount: int = 0


class SearchResponse(BaseModel):
    """检索结果。"""

    query: str
    plan: SearchPlan
    sourceLabel: str = Field(description="实际命中的数据源展示名(降级链走到哪就是谁)")
    papers: list[Paper]


# ── 推荐候选(REQ-004) ─────────────────────────────────────


class CandidateQuery(BaseModel):
    """一路取候选的检索词。

    `tag` 是调用方(Java 侧)的标签 key,原样带回去 —— 它不参与检索,
    只用来告诉调用方"这篇是命中哪个兴趣捞上来的",供打分与生成推荐理由。
    """

    tag: str
    keywords: str = Field(min_length=1, max_length=200)


class CandidateRequest(BaseModel):
    """按若干检索词批量取推荐候选。

    <p>与 `/search` 的区别:**不做查询拆解,也不走大模型** ——
    兴趣标签本身就带着检索词,没什么要拆的。少一次模型调用,少十秒等待。
    """

    queries: list[CandidateQuery] = Field(min_length=1, max_length=8)
    perQuery: int = Field(default=8, ge=1, le=20, description="每路取多少条")


class CandidatePaper(Paper):
    """候选论文 = 普通论文 + 它命中了哪几路检索。

    同一篇被多个兴趣命中时全部记下来 —— 调用方靠它决定"最相关的是哪个兴趣"。
    """

    matchedTags: list[str] = Field(default_factory=list)


class CandidateResponse(BaseModel):
    sourceLabel: str
    papers: list[CandidatePaper]


# ── 精读问答(REQ-003) ─────────────────────────────────────


class QaTurn(BaseModel):
    """一轮对话。历史由前端持有并随请求带上,服务端不维持会话状态。"""

    role: str = Field(pattern="^(user|assistant)$")
    content: str = Field(min_length=1, max_length=8000)


class QaRequest(BaseModel):
    # 用 arXiv ID 而不是本地 paperId:全文是从 arXiv 抓的,这个服务不认识本地库
    arxivId: str = Field(min_length=3, max_length=64)
    question: str = Field(min_length=2, max_length=2000)
    history: list[QaTurn] = Field(default_factory=list, max_length=40)


class Citation(BaseModel):
    """一条依据。

    `excerpt` 是**后端从原文直接取的**,不经过模型 —— 让它抄原文,它就会编原文,
    而编造的引用比答错更糟:它看起来像证据。
    """

    index: int
    title: str
    excerpt: str


class QaResponse(BaseModel):
    answer: str
    citations: list[Citation]
    omittedTurns: int = Field(
        default=0, description="因为超出上下文预算被丢掉的旧轮次数。丢的时候要让用户知道"
    )


class ArxivLookupRequest(BaseModel):
    """按编号取论文元数据。"""

    arxivId: str = Field(min_length=3, max_length=64)


class ArxivLookupResponse(BaseModel):
    """取到的论文;`found` 为 false 时 `paper` 为 null。

    <p>**用 found 而不是 404**:编号写错了是常见情况,调用方据此给一句人话,
    不必把"没找到"当异常处理。
    """

    found: bool
    paper: Paper | None = None


class ArxivTitleLookupRequest(BaseModel):
    """按标题找 arXiv 上的预印本。"""

    title: str = Field(min_length=2, max_length=512)


class ArxivTitleLookupResponse(BaseModel):
    """候选列表。

    <p>**返回列表而不是"找到的那一篇"**:判"是不是同一篇"的规则(标题 + 作者 + 年份)
    在 Java 侧的 `PaperMatcher` 里 —— 这里不重写第二套,只把 arXiv 给的候选原样交出去。

    <p>**空列表是正常结果**:arXiv 上确实没有这篇的预印本,不是错误。
    """

    candidates: list[Paper] = Field(default_factory=list)
