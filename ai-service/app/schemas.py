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

    # 以下两个只有展示用途,不参与落库
    citationCount: int = 0


class SearchResponse(BaseModel):
    """检索结果。"""

    query: str
    plan: SearchPlan
    sourceLabel: str = Field(description="实际命中的数据源展示名(降级链走到哪就是谁)")
    papers: list[Paper]
