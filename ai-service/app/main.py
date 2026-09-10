"""PaperPulse AI 服务入口。

后续按路线图逐步实现:检索 Agent(W2)、精读问答(W6)、推荐闭环(W6-7)。
"""

from fastapi import FastAPI

app = FastAPI(title="PaperPulse AI Service", version="0.1.0")


@app.get("/health")
def health() -> dict:
    """健康检查。"""
    return {"status": "ok", "service": "ai-service", "version": "0.1.0"}
