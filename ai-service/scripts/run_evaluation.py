"""离线评测入口(REQ-006 阶段 1)。

跑一次会:构造评测集(首次要联网抓 OpenAlex,之后走缓存)→ 三个方法各排一遍 → 打印对比表。

运行(在项目根目录):
    ai-service\\.venv\\Scripts\\python.exe -X utf8 ai-service\\scripts\\run_evaluation.py
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.eval.datasets import BuildConfig  # noqa: E402
from app.eval.runner import build_and_run  # noqa: E402

# 缓存放在 ai-service 下(已被 .gitignore 忽略)。构造一次要发几十个请求,
# 调指标时不该重抓。
CACHE_DIR = Path(__file__).resolve().parents[1] / ".cache" / "eval"

if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")

    print("构造评测集(首次会联网抓 OpenAlex,之后走本机缓存)…")
    table, cases = build_and_run(CACHE_DIR, BuildConfig())
    print()
    print(table)
    print()
    print("局限(见 docs/design/F11-离线评测.md 第 9 节):")
    print("  · 引用关系是代理信号,不等于真实偏好")
    print("  · 兴趣关键词取自 OpenAlex 主题词表,与线上标签词表不是同一套")
    print("  · 评的是**排序**(固定候选池),线上还多一步按标签检索取候选")
    print("  · 样本量受接口限流约束,结论的统计显著性有限")
