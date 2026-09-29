"""PaperPulse AI 服务入口。

当前提供:健康检查、论文检索(REQ-002)。
后续按需求逐步实现:精读问答(REQ-003)、推荐闭环(REQ-004)。
"""

from fastapi import FastAPI, HTTPException

from .agent import analyze
from .llm import LlmError
from .schemas import SearchRequest, SearchResponse
from .sources import AllSourcesFailed, search_papers

VERSION = "0.2.0"

app = FastAPI(title="PaperPulse AI Service", version=VERSION)


@app.get("/health")
def health() -> dict:
    """健康检查。"""
    return {"status": "ok", "service": "ai-service", "version": VERSION}


@app.post("/search", response_model=SearchResponse)
def search(payload: SearchRequest) -> SearchResponse:
    """自然语言 → Agent 拆解 → 检索 → 带来源的论文列表。

    服务本身不落库:论文由 Java 后端接收后写入,保证入库的数据都经过鉴权的调用方。
    """
    try:
        plan = analyze(payload.query)
    except LlmError as exc:
        # 502:上游(大模型)出问题,不是调用方的错,别报成 400
        raise HTTPException(status_code=502, detail=f"查询拆解失败:{exc}") from exc

    try:
        outcome = search_papers(
            plan.keywords,
            limit=payload.limit,
            year_from=plan.yearFrom,
            year_to=plan.yearTo,
        )
    except AllSourcesFailed as exc:
        # 503:依赖的外部数据源都不可用,属于"暂时不可用",不是"请求有问题"
        raise HTTPException(status_code=503, detail=str(exc)) from exc

    return SearchResponse(
        query=payload.query,
        plan=plan,
        sourceLabel=outcome.source_label,
        papers=outcome.papers,
    )
