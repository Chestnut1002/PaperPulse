"""三个评测方法的单测。不联网 —— 给固定的候选池,只看排序对不对。"""

import random

from app.eval.methods import INTEREST_WEIGHT, RECENCY_MAX, rank_ours, rank_popular, rank_random, score
from app.eval.models import Candidate, EvalUser


def candidate(paper_id, topics=(), year=2020, cited_by=0):
    return Candidate(id=paper_id, title=paper_id, year=year, cited_by=cited_by,
                     topics=tuple(topics))


def user(interests=(), cutoff_year=2024):
    return EvalUser(user_id="u", history=(), relevant=frozenset(),
                    interests=tuple(interests), cutoff_year=cutoff_year)


class TestRankRandom:
    def test_返回全部候选且不重复(self):
        pool = [candidate(str(i)) for i in range(5)]

        ranked = rank_random(pool, user(), random.Random(1))

        assert sorted(ranked) == ["0", "1", "2", "3", "4"]

    def test_同一个种子结果可复现(self):
        pool = [candidate(str(i)) for i in range(20)]

        first = rank_random(pool, user(), random.Random(42))
        second = rank_random(pool, user(), random.Random(42))

        assert first == second


class TestRankPopular:
    def test_按被引数降序(self):
        pool = [candidate("low", cited_by=1), candidate("high", cited_by=99), candidate("mid", cited_by=50)]

        assert rank_popular(pool, user()) == ["high", "mid", "low"]

    def test_同被引数时顺序稳定(self):
        pool = [candidate("b", cited_by=5), candidate("a", cited_by=5)]

        assert rank_popular(pool, user()) == ["a", "b"]


class TestScoreOurs:
    def test_命中高权重兴趣并新近的分数最高(self):
        interests = [("machine learning", 5), ("recommender system", 2)]
        cutoff = 2024

        # 命中权重 5 且就是当年:5 × 2 + 4 = 14
        assert score(candidate("a", ["Machine Learning"], year=cutoff), user(interests, cutoff)) == 14
        # 命中权重 2:2 × 2 + 4 = 8
        assert score(candidate("b", ["Recommender System"], year=cutoff), user(interests, cutoff)) == 8
        # 一个都没命中:0 × 2 + 4 = 4
        assert score(candidate("c", ["Cancer Research"], year=cutoff), user(interests, cutoff)) == 4

    def test_多个关键词命中时取权重最高的那个(self):
        interests = [("machine learning", 5), ("cancer", 1)]

        # 两个都命中,取 5 而不是相加:5 × 2 + 4 = 14
        assert score(candidate("a", ["Machine Learning", "Cancer"], year=2024),
                     user(interests, 2024)) == 14

    def test_新近度随年份递减_超过窗口不再扣(self):
        interests = [("ml", 5)]  # 假设权重 5

        def points(year):
            return score(candidate("a", ["ML"], year=year), user(interests, 2024)) - 5 * INTEREST_WEIGHT

        assert points(2024) == RECENCY_MAX
        assert points(2023) == RECENCY_MAX - 1
        assert points(2021) == RECENCY_MAX - 3
        assert points(2020) == 0
        assert points(2000) == 0

    def test_年份未知时给零而不是倒扣(self):
        # 缺年份是"记录没写",不是"这篇很旧" —— 倒扣会系统性压低这一类候选
        interests = [("ml", 5)]

        assert score(candidate("a", ["ML"], year=None), user(interests, 2024)) == 5 * INTEREST_WEIGHT

    def test_基准年是切分年而不是今天(self):
        interests = [("ml", 5)]
        paper = candidate("a", ["ML"], year=2024)

        # 同一篇论文,切分年不同 → 新近度不同(今天的年份不该参与)
        assert score(paper, user(interests, cutoff_year=2024)) > score(paper, user(interests, cutoff_year=2030))

    def test_主题匹配用子串而不是完全相同(self):
        # 关键词是短的,主题是长短语 —— 只能靠子串比
        assert score(candidate("a", ["Advances in Machine Learning for X"], year=2024),
                     user([("machine learning", 5)], 2024)) == 5 * INTEREST_WEIGHT + RECENCY_MAX

    def test_大小写不影响(self):
        assert score(candidate("a", ["MACHINE LEARNING"], year=2024),
                     user([("Machine Learning", 5)], 2024)) == 5 * INTEREST_WEIGHT + RECENCY_MAX


class TestRankOurs:
    def test_兴趣权重主导排序(self):
        interests = [("machine learning", 5), ("cancer", 1)]
        pool = [
            candidate("weak", ["Cancer"], year=2024),        # 1 × 2 + 4 = 6
            candidate("strong", ["Machine Learning"], year=2024),  # 5 × 2 + 4 = 14
        ]

        assert rank_ours(pool, user(interests, 2024)) == ["strong", "weak"]

    def test_兴趣相同时新近度决定先后(self):
        interests = [("ml", 5)]
        pool = [
            candidate("old", ["ML"], year=2019),
            candidate("new", ["ML"], year=2024),
        ]

        assert rank_ours(pool, user(interests, 2024)) == ["new", "old"]

    def test_同分时按_id_稳定排序(self):
        interests = [("ml", 5)]
        pool = [candidate("b", ["ML"], year=2024), candidate("a", ["ML"], year=2024)]

        assert rank_ours(pool, user(interests, 2024)) == ["a", "b"]

    def test_返回全部候选且不重复(self):
        pool = [candidate(str(i)) for i in range(5)]

        ranked = rank_ours(pool, user([("ml", 5)], 2024))

        assert sorted(ranked) == ["0", "1", "2", "3", "4"]
