"""BGE 向量化(评测阶段 2)。

**这是一个重依赖:模型约 2GB,运行要 torch。** 所以它整个模块都是**按需加载**的 ——
`methods.py` 不在顶层导入它,随机/热门/我们的算法那三个方法不该被它拖住。
"""

import os
from functools import lru_cache

# 必须**在导入 sentence_transformers 之前**设置:它读的是这个环境变量。
# 本机到不了 huggingface.co(实测连接失败),国内镜像可达。
os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")

# 用哪个模型。
#
# 原本选的是 BGE-M3(项目的既定 embedding 选型),但**本机网络下它下不动**:
# hf-mirror 实测从 44 MB/分掉到 9 MB/分,2.2GB 要四个小时,中途还断过一次。
# 换成 bge-base-en-v1.5(约 440MB)—— 最常用的标准英文模型,足以回答
# "语义向量 vs 关键词匹配"这个问题;评测语料本来就是英文标题。
#
# 换模型会改变数字,所以**必须记进实验记录**。要换回去就设 EVAL_EMBEDDING_MODEL。
MODEL_NAME = os.environ.get("EVAL_EMBEDDING_MODEL", "BAAI/bge-base-en-v1.5")

# 一次编码多少条。太大吃内存,太小浪费吞吐。
_BATCH_SIZE = 32


@lru_cache(maxsize=1)
def _model():
    """模型只加载一次 —— 加载本身要好几秒。"""
    from sentence_transformers import SentenceTransformer

    return SentenceTransformer(MODEL_NAME)


def encode(texts: list[str]) -> list[list[float]]:
    """把一批文本变成**已归一化**的向量。

    <p>归一化之后 cosine 相似度就等于点积,省一次除法,也不会因为漏归一化而算错。
    """
    if not texts:
        return []
    vectors = _model().encode(
        texts,
        batch_size=_BATCH_SIZE,
        normalize_embeddings=True,
        show_progress_bar=len(texts) > 200,
    )
    return [vector.tolist() for vector in vectors]


def paper_text(title: str, topics: tuple[str, ...]) -> str:
    """一篇论文参与向量化的文本。

    <p>标题 + 主题短语。**这里没有用摘要** —— OpenAlex 的摘要是倒排索引格式,
    还原它要额外一段代码;而主题短语已经提供了足够的语义信号。

    <p>与生产的差别要记住:线上有摘要可用。这一版评的是"**语义向量 vs 关键词匹配**"这件事本身,
    不是"最好的 embedding 方案能到什么程度"。
    """
    return title + " " + " ".join(topics)


def cosine(left: list[float], right: list[float]) -> float:
    """两个已归一化向量的相似度 = 点积。"""
    return sum(a * b for a, b in zip(left, right))


def build_vectors(cases) -> dict[str, list[float]]:
    """把所有要用到的论文**一次**编码完,返回 `论文 id -> 向量`。

    <p>不做"每个用户编一次":同一篇论文会出现在多个用户的候选池里,逐次编码会把同一篇重复算很多遍。

    <p>这个函数一被调用就会加载模型(约 2GB),所以只有真的要跑 embedding 基线时才调用它。
    """
    texts: dict[str, str] = {}
    for case in cases:
        for candidate in (*case.user.history, *case.candidates):
            texts.setdefault(candidate.id, paper_text(candidate.title, candidate.topics))

    ids = list(texts)
    vectors = encode([texts[paper_id] for paper_id in ids])
    return dict(zip(ids, vectors))
