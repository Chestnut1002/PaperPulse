"""评测指标的单测。

用手算得出的例子把公式钉死 —— 指标算错不会崩,只会静默给出没有意义的数字。
"""

import pytest

from app.eval.metrics import average, evaluate, ndcg_at_k, recall_at_k

RANKED = ["a", "b", "c", "d", "e"]


class TestRecallAtK:
    def test_全部命中(self):
        assert recall_at_k(RANKED, frozenset({"a", "c"}), 3) == 1.0

    def test_只命中一半(self):
        # 前 3 个里只有 a 相关,相关项共 2 个
        assert recall_at_k(RANKED, frozenset({"a", "d"}), 3) == 0.5

    def test_一个都没命中(self):
        assert recall_at_k(RANKED, frozenset({"z"}), 5) == 0.0

    def test_相关项排在K之外不算命中(self):
        assert recall_at_k(RANKED, frozenset({"d", "e"}), 2) == 0.0

    def test_没有相关项时为零而不是除零(self):
        assert recall_at_k(RANKED, frozenset(), 5) == 0.0

    def test_K_超过候选数时不越界(self):
        assert recall_at_k(["a"], frozenset({"a"}), 10) == 1.0


class TestNdcgAtK:
    def test_相关项排在最前时为满分(self):
        assert ndcg_at_k(["a", "b", "c"], frozenset({"a", "b"}), 3) == pytest.approx(1.0)

    def test_手算例子_位置0与2(self):
        # 命中在位置 0 和 2:DCG = 1/log2(2) + 1/log2(4) = 1 + 0.5 = 1.5
        # 理想排序(两个相关项都在最前)= 1 + 1/log2(3) ≈ 1.63093
        assert ndcg_at_k(RANKED, frozenset({"a", "c"}), 3) == pytest.approx(1.5 / (1 + 1 / 1.5849625))

    def test_排得靠后分数明显更低(self):
        # 这是 NDCG 相对 Recall 的价值:同样是"命中两个",位置差很多分数就差很多
        front = ndcg_at_k(RANKED, frozenset({"a", "b"}), 5)
        back = ndcg_at_k(RANKED, frozenset({"d", "e"}), 5)
        assert front > back
        assert back == pytest.approx((1 / 2.3219281 + 1 / 2.5849625) / (1 + 1 / 1.5849625))

    def test_Recall_相同而_NDCG_不同(self):
        # 两个排序的 Recall@2 都是 0.5,但"排在第一"与"排在第二"应当被区分开
        first = ["a", "x"]
        second = ["x", "a"]
        relevant = frozenset({"a", "y"})
        assert recall_at_k(first, relevant, 2) == recall_at_k(second, relevant, 2)
        assert ndcg_at_k(first, relevant, 2) > ndcg_at_k(second, relevant, 2)

    def test_没有相关项时为零(self):
        assert ndcg_at_k(RANKED, frozenset(), 5) == 0.0

    def test_相关项多于K时理想值按K截断(self):
        # 3 个相关项但 K=2 —— 理想排序只能放 2 个
        assert ndcg_at_k(["a", "b"], frozenset({"a", "b", "c"}), 2) == pytest.approx(1.0)


class TestEvaluate:
    def test_一次算出多个K(self):
        scores = evaluate(RANKED, frozenset({"a", "c"}), (3, 5))

        assert set(scores) == {"recall@3", "ndcg@3", "recall@5", "ndcg@5"}
        assert scores["recall@3"] == 1.0
        assert scores["recall@5"] == 1.0

    def test_平均(self):
        rows = [{"recall@5": 1.0, "ndcg@5": 0.5}, {"recall@5": 0.0, "ndcg@5": 0.1}]

        assert average(rows) == {"recall@5": 0.5, "ndcg@5": 0.3}

    def test_空列表时返回空(self):
        assert average([]) == {}
