"""embedding 基线的单测。

**全部用手造的向量,不加载模型** —— 模型的加载(还有下载)不该是单测的前提。
排序逻辑本身与模型无关,拿假向量一样能钉死。
"""

import pytest

from app.eval import embeddings
from app.eval.embeddings import cosine, paper_text
from app.eval.methods import EmbeddingRanker, _normalize
from app.eval.models import Candidate, EvalUser

# 已经归一化过的二维向量,方便手算点积
RIGHT = [1.0, 0.0]
UP = [0.0, 1.0]
DIAGONAL = [0.6, 0.8]


def candidate(paper_id, title="t", topics=()):
    return Candidate(id=paper_id, title=title, year=2020, cited_by=0, topics=tuple(topics))


def user(history_ids, user_id="u"):
    return EvalUser(
        user_id=user_id,
        history=tuple(candidate(paper_id) for paper_id in history_ids),
        relevant=frozenset(),
        interests=(),
        cutoff_year=2020,
    )


class TestCosine:
    def test_同向为一(self):
        assert cosine(RIGHT, RIGHT) == pytest.approx(1.0)

    def test_垂直为零(self):
        assert cosine(RIGHT, UP) == pytest.approx(0.0)

    def test_手算的夹角(self):
        assert cosine(RIGHT, DIAGONAL) == pytest.approx(0.6)


class TestNormalize:
    def test_归一化后长度为1(self):
        assert _normalize([3.0, 4.0]) == pytest.approx([0.6, 0.8])

    def test_零向量原样返回而不是除零(self):
        assert _normalize([0.0, 0.0]) == [0.0, 0.0]


class TestPaperText:
    def test_标题加主题(self):
        assert paper_text("A Paper", ("Machine Learning", "NLP")) == "A Paper Machine Learning NLP"

    def test_没有主题时只有标题(self):
        assert paper_text("A Paper", ()) == "A Paper "


class TestEmbeddingRanker:
    def test_与历史接近的候选排前面(self):
        vectors = {"h": RIGHT, "close": DIAGONAL, "far": UP}
        ranker = EmbeddingRanker(vectors)

        ranked = ranker([candidate("far"), candidate("close")], user(["h"]))

        assert ranked == ["close", "far"]

    def test_历史有多篇时取平均(self):
        vectors = {"h1": RIGHT, "h2": UP, "on_axis": RIGHT, "off_axis": [-1.0, 0.0]}
        ranker = EmbeddingRanker(vectors)

        # 两篇历史的平均方向是 [0.5, 0.5];与 RIGHT 夹角 45°,与 [-1,0] 相反
        ranked = ranker([candidate("off_axis"), candidate("on_axis")], user(["h1", "h2"]))

        assert ranked == ["on_axis", "off_axis"]

    def test_历史没有向量时保持原序(self):
        ranker = EmbeddingRanker({"a": RIGHT})

        ranked = ranker([candidate("a"), candidate("b")], user(["missing"]))

        assert ranked == ["a", "b"]

    def test_候选缺向量时排到最后而不是报错(self):
        vectors = {"h": RIGHT, "has": DIAGONAL}
        ranker = EmbeddingRanker(vectors)

        ranked = ranker([candidate("missing"), candidate("has")], user(["h"]))

        assert ranked == ["has", "missing"]

    def test_同分时按_id_稳定排序(self):
        vectors = {"h": RIGHT, "b": RIGHT, "a": RIGHT}
        ranker = EmbeddingRanker(vectors)

        assert ranker([candidate("b"), candidate("a")], user(["h"])) == ["a", "b"]

    def test_返回全部候选且不重复(self):
        vectors = {"h": RIGHT, "a": RIGHT, "b": UP}
        ranker = EmbeddingRanker(vectors)

        ranked = ranker([candidate("a"), candidate("b")], user(["h"]))

        assert sorted(ranked) == ["a", "b"]


class TestBuildVectors:
    def test_每篇论文只编码一次(self, monkeypatch):
        """同一篇论文会出现在多个用户的池子里,重复编码是纯粹的浪费。"""
        encoded: list[list[str]] = []

        def fake_encode(texts):
            encoded.append(texts)
            return [[1.0, 0.0] for _ in texts]

        monkeypatch.setattr(embeddings, "encode", fake_encode)

        class Case:
            def __init__(self, history, candidates):
                self.user = type("U", (), {"history": history})()
                self.candidates = candidates

        shared = candidate("shared", title="共享的一篇")
        cases = [
            Case([shared], [candidate("a")]),
            Case([shared], [candidate("b")]),
        ]

        vectors = embeddings.build_vectors(cases)

        assert len(encoded) == 1, "只该调用一次 encode"
        # 三篇不同的论文(shared 出现两次但只编一次)—— 两条 "t " 分别来自 a 和 b
        assert sorted(encoded[0]) == sorted(["共享的一篇 ", "t ", "t "])
        assert set(vectors) == {"shared", "a", "b"}
