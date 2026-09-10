"""PaperPulse AI 服务入口。

后续按需求逐步实现:检索 Agent(REQ-002)、精读问答(REQ-003)、推荐闭环(REQ-004)。
"""

from fastapi import FastAPI

app = FastAPI(title="PaperPulse AI Service", version="0.1.0")


@app.get("/health")
def health() -> dict:
    """健康检查。"""
    return {"status": "ok", "service": "ai-service", "version": "0.1.0"}
