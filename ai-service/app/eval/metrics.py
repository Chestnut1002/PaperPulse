"""离线评测指标。

**纯函数,而且必须单独单测。** 指标算错是**静默的** —— 数字照样输出,只是没有意义,
不像崩溃那样会自己冒出来。所以用手算得出的例子把公式钉死。
"""

import math


def recall_at_k(ranked: list[str], relevant: frozenset[str], k: int) -> float:
    """前 K 个里命中多少个相关项,除以相关项总数。

    @param ranked 排序后的 id 列表(越靠前越推荐)
    """
    if not relevant:
        return 0.0
    hits = sum(1 for item in ranked[:k] if item in relevant)
    return hits / len(relevant)


def ndcg_at_k(ranked: list[str], relevant: frozenset[str], k: int) -> float:
    """带位置折扣的命中率:排在越靠前贡献越大。

    <p>为什么不能只看 Recall:Recall@10 把"排在第 1 位"和"排在第 10 位"算得一样重,
    而推荐系统里这两者差别巨大。
    """
    if not relevant:
        return 0.0

    # 位置 i(从 0 数)的折扣是 log2(i + 2):i=0 时为 1,i=1 时约 0.63
    dcg = sum(
        1.0 / math.log2(index + 2)
        for index, item in enumerate(ranked[:k])
        if item in relevant
    )

    # 理想排序:所有相关项都排在最前面
    ideal = sum(1.0 / math.log2(index + 2) for index in range(min(len(relevant), k)))

    return dcg / ideal if ideal else 0.0


def evaluate(ranked: list[str], relevant: frozenset[str], k_values: tuple[int, ...]) -> dict[str, float]:
    """一次算出多个 K 下的两个指标,键形如 `recall@5` / `ndcg@10`。"""
    scores: dict[str, float] = {}
    for k in k_values:
        scores[f"recall@{k}"] = recall_at_k(ranked, relevant, k)
        scores[f"ndcg@{k}"] = ndcg_at_k(ranked, relevant, k)
    return scores


def average(per_user: list[dict[str, float]]) -> dict[str, float]:
    """把每个用户的分数平均成整体分数。"""
    if not per_user:
        return {}
    keys = per_user[0].keys()
    return {key: sum(row.get(key, 0.0) for row in per_user) / len(per_user) for key in keys}
