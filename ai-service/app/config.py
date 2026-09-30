"""集中读取配置。

环境变量只在这里出现一次 —— 散落到各个模块里读,迟早会出现"某个地方读了另一个名字"
这种只有运行时才暴露的问题。
"""

import os
from pathlib import Path

from dotenv import load_dotenv

# ai-service/.env,不进 Git。load_dotenv 不会覆盖已存在的环境变量,
# 所以容器里注入的环境变量优先级更高。
ENV_PATH = Path(__file__).resolve().parents[1] / ".env"
load_dotenv(ENV_PATH)

# Semantic Scholar 的可选 API key。不填也能用,但匿名共享池经常 429;
# 免费申请一个(https://www.semanticscholar.org/product/api)就能拿到独立配额。
SEMANTIC_SCHOLAR_API_KEY = os.getenv("SEMANTIC_SCHOLAR_API_KEY", "")

DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")

# 带上项目标识与联系方式 —— 学术 API 的礼貌,也便于对方在异常时联系我们
USER_AGENT = "PaperPulse/0.1 (mailto:paperpulse@example.com)"

DEFAULT_LIMIT = 5
MAX_LIMIT = 20

# ── 精读问答(REQ-003) ────────────────────────────────────
#
# 送给模型的输入上限(估计的 token 数)。全文常驻上下文,所以这个预算大部分被论文占掉,
# 剩下的留给对话历史。
QA_CONTEXT_BUDGET = int(os.getenv("QA_CONTEXT_BUDGET", "48000"))

# 没有 DeepSeek 的分词器,只能按字符估 token。英文约 4 字符 / token,这里取 3 更保守 ——
# 宁可少放几轮对话,也不要因为超限被接口拒绝。
QA_CHARS_PER_TOKEN = 3

# 全文缓存目录。重启不丢。
# 默认锚在 ai-service 目录下而不是当前工作目录 —— 从别处启动服务时后者会跑偏。
_AI_SERVICE_DIR = Path(__file__).resolve().parents[1]
FULLTEXT_CACHE_DIR = _AI_SERVICE_DIR / os.getenv("FULLTEXT_CACHE_DIR", ".cache/fulltext")

# 单次检索最多等多久(秒)。S2 偶尔很慢,超时后由降级链接手。
REQUEST_TIMEOUT = 25
