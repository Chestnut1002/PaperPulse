"""评测里的三个方法。都是"给一个固定候选池,排出一个顺序",不联网。

阶段的划分见 `docs/design/F11-离线评测.md`:这里只有随机、热门、我们的算法三个;
embedding / SASRec / LLM 是后面的阶段。
"""

import random
from typing import Protocol

from .models import Candidate, EvalUser

# 与生产端同一套常数。改了一边要同步另一边 —— 见设计文档 3.5 的适配说明。
INTEREST_WEIGHT = 2
RECENCY_MAX = 4
RECENCY_WINDOW = 4


class Ranker(Protocol):
    """一种排序方法。"""

    def __call__(self, candidates: list[Candidate], user: EvalUser) -> list[str]: ...


def rank_random(candidates: list[Candidate], user: EvalUser, rng: random.Random) -> list[str]:
    """下界。**任何算法打不过它,说明实现有 bug** —— 这正是它存在的意义。"""
    pool = [candidate.id for candidate in candidates]
    rng.shuffle(pool)
    return pool


def rank_popular(candidates: list[Candidate], user: EvalUser) -> list[str]:
    """按被引数排序。

    <p>经典强基线:老论文被引多,容易被它排到前面。**推荐系统打不过它就该反思** ——
    说明个性化没有带来任何额外价值。
    """
    return [candidate.id for candidate in sorted(candidates, key=lambda c: (-c.cited_by, c.id))]


def rank_ours(candidates: list[Candidate], user: EvalUser) -> list[str]:
    """当前生产算法的**打分函数移植版**。

    <p><b>这是移植版,不是线上那条链路。</b>生产上还有"按兴趣标签去外部检索取候选"这一步,
    而固定候选池里没有检索 —— 所以这里评的是**排序**。报告里必须写明这条边界,
    否则这个分数会被误读成整条线的分数。
    """
    scored = [(score(candidate, user), candidate.id) for candidate in candidates]
    # 同分时按 id 排,保证同一份数据每次跑出的顺序一致
    scored.sort(key=lambda pair: (-pair[0], pair[1]))
    return [paper_id for _, paper_id in scored]


def score(candidate: Candidate, user: EvalUser) -> float:
    """`兴趣匹配 × 2 + 新近度`,与生产完全一致。"""
    return (
        _interest_match(candidate, user) * INTEREST_WEIGHT
        + _recency(candidate, user.cutoff_year)
    )


def _interest_match(candidate: Candidate, user: EvalUser) -> int:
    """命中的兴趣关键词里**权重最高**的那个;一个都没命中给 0。

    <p>命中判定用**子串**:兴趣关键词是短的(如 "machine learning"),
    而候选的主题是长短语(如 "Advances in Machine Learning for X")。
    这是近似 —— 生产环境里"命中哪个标签"是检索决定的,固定池里只能靠文本比。
    """
    topic_text = " ".join(candidate.topics).lower()
    matched = [weight for keyword, weight in user.interests if keyword.lower() in topic_text]
    return max(matched, default=0)


def _recency(candidate: Candidate, cutoff_year: int) -> int:
    """新近度。规则与生产一致,**但基准年是切分年而不是今天** ——
    这是历史数据,拿今天去算会让每一篇都"很旧",把新近度信号整体压平。"""
    if candidate.year is None:
        return 0
    age = max(0, cutoff_year - candidate.year)
    return max(0, RECENCY_MAX - min(age, RECENCY_WINDOW))
