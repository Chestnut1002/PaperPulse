"""hello_llm.py —— 让你的程序第一次跟 DeepSeek 说话。

这是 PaperPulse 的第一步:后面所有的检索 Agent、精读问答、个性化推荐,
本质上都是在这个基础上加东西。

运行(在项目根目录):
    py ai-service/scripts/hello_llm.py
"""
import os
from pathlib import Path

import httpx
from dotenv import load_dotenv

# ① 把 ai-service/.env 里的配置读进"环境变量"
#    .env 存着 API key,它不进 Git,只在本机
ENV_PATH = Path(__file__).resolve().parents[1] / ".env"
load_dotenv(ENV_PATH)

API_KEY = os.getenv("DEEPSEEK_API_KEY")
BASE_URL = os.getenv("DEEPSEEK_BASE_URL")
MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-flash")

if not API_KEY:
    raise SystemExit(f"没找到 DEEPSEEK_API_KEY,请检查 {ENV_PATH}")

# ② 需要"证明身份"的请求都带上这个请求头
HEADERS = {"Authorization": f"Bearer {API_KEY}"}


def list_models() -> None:
    """问 DeepSeek:你有哪些模型可用?"""
    response = httpx.get(f"{BASE_URL}/models", headers=HEADERS, timeout=30)
    print("状态码:", response.status_code)
    for model in response.json()["data"]:
        print(" -", model["id"])


def chat(question: str, model: str = MODEL) -> str:
    """把一个提问发给大模型,拿回它的回答。"""
    response = httpx.post(
        f"{BASE_URL}/chat/completions",
        headers=HEADERS,
        json={
            "model": model,
            "messages": [{"role": "user", "content": question}],
        },
        timeout=60,
    )
    response.raise_for_status()  # 出错就抛异常,不悄悄忽略
    return response.json()["choices"][0]["message"]["content"]


if __name__ == "__main__":
    print("=== 1. 看看有哪些模型可用 ===")
    list_models()

    print("\n=== 2. 问它一个问题 ===")
    print(chat("什么是推荐系统?一句话说"))
