"""PaperPulse AI 服务入口。

当前提供:健康检查、论文检索(REQ-002)。
后续按需求逐步实现:精读问答(REQ-003)、推荐闭环(REQ-004)。
"""

from fastapi import FastAPI, HTTPException

from . import config

from .agent import analyze
from .llm import LlmError
from .reading.fulltext import FullTextStore, FullTextUnavailable
from .reading.qa import ContextTooLong, ask
from .schemas import (
    ArxivLookupRequest,
    ArxivLookupResponse,
    CandidateRequest,
    CandidateResponse,
    Citation,
    QaRequest,
    QaResponse,
    SearchRequest,
    SearchResponse,
)
from .sources import AllSourcesFailed, fetch_arxiv_by_id, find_candidates, search_papers

# 全文缓存在服务启动时建一次,进程内复用
_full_text_store = FullTextStore(config.FULLTEXT_CACHE_DIR)

# 一条依据里摘多少字符。够看清"取自哪一段",又不至于把整节塞进响应
_EXCERPT_CHARS = 320

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


@app.post("/recommend/candidates", response_model=CandidateResponse)
def recommend_candidates(payload: CandidateRequest) -> CandidateResponse:
    """按若干检索词批量取推荐候选(REQ-004)。

    <p>**不调用大模型**:兴趣标签本身就带着检索词(见 Java 侧的 `InterestTag`),
    没有"自然语言"需要拆解。少一次模型调用,少十秒等待。

    <p>候选的排序与筛选不在这里 —— 这里只负责"把可能相关的论文捞回来",
    打分要用到用户的行为数据,那是 Java 侧的事。
    """
    try:
        labels, papers = find_candidates(
            [(query.tag, query.keywords) for query in payload.queries],
            per_query=payload.perQuery,
        )
    except AllSourcesFailed as exc:
        # 503:上游都不可用,属于"暂时不可用",不是调用方的问题
        raise HTTPException(status_code=503, detail=str(exc)) from exc

    return CandidateResponse(sourceLabel="、".join(labels), papers=papers)


@app.post("/lookup/arxiv", response_model=ArxivLookupResponse)
def lookup_arxiv(payload: ArxivLookupRequest) -> ArxivLookupResponse:
    """按编号取一篇 arXiv 论文的元数据。

    <p>用在"我手里有一篇论文想读"那条路上 —— 用户不必先搜一遍。
    元数据拿回来后由 Java 侧落库,再进精读。
    """
    try:
        paper = fetch_arxiv_by_id(payload.arxivId)
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=f"取 arXiv 元数据失败:{exc}") from exc

    # 编号不存在时返回 found=false,而不是 404 —— 写错编号是常见情况,
    # 调用方据此给一句人话即可,没必要把它当异常
    return ArxivLookupResponse(found=paper is not None, paper=paper)


@app.post("/qa", response_model=QaResponse)
def qa(payload: QaRequest) -> QaResponse:
    """就一篇 arXiv 论文回答问题(REQ-003)。

    <p><b>全文常驻上下文,不走 RAG。</b>一篇论文去掉参考文献后约 2 万 token,装得下;
    跨论文检索才需要 RAG。

    <p>对话历史由调用方带上 —— 这个服务不维持会话状态,重启不丢对话。

    <p><b>引用是构造出来的,不是模型抄的</b>:模型只负责"回指哪一节",
    摘录由这里从原文直接取。让它抄原文,它就会编原文。
    """
    try:
        full_text = _full_text_store.get(payload.arxivId)
    except FullTextUnavailable as exc:
        # 404:这篇论文没有可精读的全文 —— 是"没有这个东西",不是"服务器坏了"
        raise HTTPException(status_code=404, detail=str(exc)) from exc

    try:
        result = ask(full_text, payload.question,
                     [(turn.role, turn.content) for turn in payload.history])
    except ContextTooLong as exc:
        # 422:请求本身没问题,是这篇论文超出了能处理的长度
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except LlmError as exc:
        raise HTTPException(status_code=502, detail=f"回答失败:{exc}") from exc

    return QaResponse(
        answer=result.answer,
        citations=_citations(full_text, result.cited_indexes),
        omittedTurns=result.omitted_turns,
    )


def _citations(full_text, indexes) -> list[Citation]:
    """按模型回指的节号,从原文取依据片段。**取不到的节号直接跳过,不猜。**"""
    citations = []
    for index in indexes:
        section = full_text.find(index)
        if section is None:
            continue
        excerpt = section.text[:_EXCERPT_CHARS]
        if len(section.text) > _EXCERPT_CHARS:
            excerpt += "…"
        citations.append(Citation(index=index, title=section.title, excerpt=excerpt))
    return citations
