"""search_papers.py —— 命令行调试工具:检索论文并在终端里看结果。

真正的检索逻辑在 `app/sources.py`(服务用的就是它),这里只负责把它接到命令行上,
避免两处各维护一份检索代码。

运行(在项目根目录):
    ai-service\\.venv\\Scripts\\python.exe ai-service\\scripts\\search_papers.py "contrastive learning recommendation"
"""

import sys
from pathlib import Path

# 让脚本能 import 到 app 包:把 ai-service 目录加进模块搜索路径。
# 用绝对路径而不是相对路径,这样从哪个目录调用都能跑。
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.sources import search_papers  # noqa: E402  (必须在 sys.path 调整之后)


def format_paper(index: int, paper: dict) -> str:
    """把一篇论文格式化成一屏可读的文本。"""
    authors = ", ".join(paper["authors"][:3]) or "(未提供)"
    abstract = (paper["abstractText"] or "(无摘要)")[:150]
    return (
        f"{index}. {paper['title']}  ({paper['publicationYear'] or '?'})\n"
        f"   作者: {authors}\n"
        f"   来源: {paper['source']} / {paper['externalId']}\n"
        f"   引用数: {paper['citationCount']}\n"
        f"   链接: {paper['url'] or '(无)'}\n"
        f"   摘要: {abstract}...\n"
    )


if __name__ == "__main__":
    query = sys.argv[1] if len(sys.argv) > 1 else "recommendation system"
    print(f"检索:{query}")
    outcome = search_papers(query)
    print(f"命中数据源:{outcome.source_label}\n")
    for i, item in enumerate(outcome.papers, start=1):
        print(format_paper(i, item))
