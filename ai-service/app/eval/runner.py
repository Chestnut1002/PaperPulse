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
METHOD_ORDER = ("随机", "热门", "我们的算法")


def run(cases: list[UserCase], seed: int) -> dict[str, dict[str, float]]:
    """对每个方法跑一遍全部用户,返回 {方法名: {指标: 平均分}}。"""
    rng = random.Random(seed)

    return {
        "随机": _evaluate_all(cases, lambda candidates, user: rank_random(candidates, user, rng)),
        "热门": _evaluate_all(cases, rank_popular),
        "我们的算法": _evaluate_all(cases, rank_ours),
    }


def _evaluate_all(cases: list[UserCase], ranker) -> dict[str, float]:
    per_user = [
        evaluate(ranker(case.candidates, case.user), case.user.relevant, K_VALUES)
        for case in cases
    ]
    return average(per_user)


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


def build_and_run(cache_dir: Path, cfg: BuildConfig | None = None) -> tuple[str, list[UserCase]]:
    """构造评测集并跑一遍。返回(表格文本, 评测用例)供调用方另作处理。"""
    settings = cfg or BuildConfig()
    source = OpenAlexSource(cache_dir)
    try:
        cases = build_users(source, settings)
    finally:
        source.close()

    if not cases:
        raise SystemExit("没有构造出任何评测用例 —— 检查缓存或放宽 min_references")

    results = run(cases, settings.seed)
    return format_table(results, len(cases)), cases
