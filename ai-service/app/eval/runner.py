"""跑一遍评测,汇总成表。"""

import random
from pathlib import Path

from .datasets import BuildConfig, OpenAlexSource, UserCase, build_users
from .metrics import average, evaluate
from .methods import rank_ours, rank_popular, rank_random

K_VALUES = (5, 10)

# 展示顺序:从弱到强。**这个顺序本身就是一条结论** ——
# 如果热门排在随机前面、我们的算法排在随机前面,说明尺子是活的;
# 三个方法分数差不多的话,多半是评测本身有问题。
METHOD_ORDER = ("随机", "热门", "我们的算法", "BGE embedding")


def run(cases: list[UserCase], seed: int, embedder=None) -> dict[str, dict[str, float]]:
    """对每个方法跑一遍全部用户,返回 {方法名: {指标: 平均分}}。

    @param embedder embedding 基线。**为 None 就不跑它** ——
                      加载模型要好几秒、编码要几分钟,不该在只调前三个方法时被拖住
    """
    rng = random.Random(seed)

    results = {
        "随机": _evaluate_all(cases, lambda candidates, user: rank_random(candidates, user, rng)),
        "热门": _evaluate_all(cases, rank_popular),
        "我们的算法": _evaluate_all(cases, rank_ours),
    }
    if embedder is not None:
        results["BGE embedding"] = _evaluate_all(cases, embedder)
    return results


def _evaluate_all(cases: list[UserCase], ranker) -> dict[str, float]:
    per_user = [
        evaluate(ranker(case.candidates, case.user), case.user.relevant, K_VALUES)
        for case in cases
    ]
    return average(per_user)


def compare(cases: list[UserCase], seed: int,
            method_a, method_b, metric: str = "ndcg@10",
            iterations: int = 2000) -> tuple[float, float, float]:
    """配对自助法:两个方法逐个用户的差,重采样求 95% 区间。

    <p>**为什么需要它**:39 个用户上"看起来高了 2%"不能直接当结论。
    区间跨过 0 就说明这个样本量下分不出高下 —— 那和"没有差别"是两回事,
    和"确实更好"更是两回事。

    <p>用**配对**而不是两组独立样本:同一个用户面对同样的候选池,
    两者的差才是"方法之差";直接把两组的分数拿来比会被用户之间的差异淹没。

    @return (平均差, 区间下界, 区间上界)
    """
    rng = random.Random(seed)

    differences = []
    for case in cases:
        ranked_a = method_a(case.candidates, case.user)
        ranked_b = method_b(case.candidates, case.user)
        scores_a = evaluate(ranked_a, case.user.relevant, K_VALUES)
        scores_b = evaluate(ranked_b, case.user.relevant, K_VALUES)
        differences.append(scores_a[metric] - scores_b[metric])

    observed = sum(differences) / len(differences)

    means = []
    for _ in range(iterations):
        resample = [differences[rng.randrange(len(differences))] for _ in differences]
        means.append(sum(resample) / len(resample))
    means.sort()

    return observed, means[int(0.025 * iterations)], means[int(0.975 * iterations) - 1]


def format_comparison(observed: float, low: float, high: float,
                      metric: str, method_a: str, method_b: str) -> str:
    verdict = (
        "分不出高下(区间跨过 0)" if low <= 0 <= high
        else ("A 更好" if low > 0 else "B 更好")
    )
    return (
        f"{method_a} 减 {method_b} 的 {metric}:"
        f"平均 {observed:+.4f},95% 区间 [{low:+.4f}, {high:+.4f}] → {verdict}"
    )


def format_table(results: dict[str, dict[str, float]], user_count: int) -> str:
    """把结果排成一张可读的表。"""
    columns = [f"recall@{k}" for k in K_VALUES] + [f"ndcg@{k}" for k in K_VALUES]

    lines = [f"评测用户数:{user_count}", ""]
    header = f"{'方法':<10}" + "".join(f"{column:>12}" for column in columns)
    lines.append(header)
    lines.append("-" * len(header))

    for method in METHOD_ORDER:
        if method not in results:
            continue
        row = results[method]
        lines.append(f"{method:<10}" + "".join(f"{row.get(column, 0.0):>12.4f}" for column in columns))

    return "\n".join(lines)


def build_and_run(cache_dir: Path, cfg: BuildConfig | None = None,
                  with_embeddings: bool = False) -> tuple[str, list[UserCase]]:
    """构造评测集并跑一遍。返回(表格文本, 评测用例)供调用方另作处理。"""
    settings = cfg or BuildConfig()
    source = OpenAlexSource(cache_dir)
    try:
        cases = build_users(source, settings)
    finally:
        source.close()

    if not cases:
        raise SystemExit("没有构造出任何评测用例 —— 检查缓存或放宽 min_references")

    embedder = None
    if with_embeddings:
        embedder = _build_embedder(cases)

    results = run(cases, settings.seed, embedder)
    table = format_table(results, len(cases))

    if embedder is not None:
        # 只看平均分容易把"几个百分点的差"读成结论 —— 小样本上必须带区间。
        # 这里复用已经建好的 embedder,**不再重新编码一遍**(那要多花两分钟)。
        observed, low, high = compare(cases, settings.seed, rank_ours, embedder)
        table += "\n\n" + format_comparison(observed, low, high, "ndcg@10",
                                            "我们的算法", "BGE embedding")

    return table, cases


def _build_embedder(cases: list[UserCase]):
    """加载模型、编码全部论文、构造 embedding 基线。

    <p>**导入放在函数里** —— `sentence-transformers` 会拉起 torch,
    只有真的要跑 embedding 时才该付这个代价。
    """
    from .embeddings import build_vectors
    from .methods import EmbeddingRanker

    print("载入嵌入模型并编码全部论文(首次要下载约 440MB,之后走本机缓存)…")
    return EmbeddingRanker(build_vectors(cases))
