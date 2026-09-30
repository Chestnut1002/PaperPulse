"""检索数据源的纯逻辑:归一化、年份过滤、条目映射、多源合并。不联网。"""

import xml.etree.ElementTree as ET

import pytest

from app import sources
from app.sources import (
    AllSourcesFailed,
    SearchOutcome,
    _candidate_key,
    _clean_text,
    _in_year_range,
    _interleave,
    _non_empty,
    arxiv_to_paper,
    crossref_to_paper,
    normalize_doi,
    semantic_scholar_to_paper,
)


class TestYearRange:
    def test_没有约束时全部通过(self):
        assert _in_year_range(1999, None, None) is True

    def test_下界(self):
        assert _in_year_range(2024, 2024, None) is True
        assert _in_year_range(2023, 2024, None) is False

    def test_上界(self):
        assert _in_year_range(2025, None, 2025) is True
        assert _in_year_range(2026, None, 2025) is False

    def test_区间(self):
        assert _in_year_range(2024, 2020, 2025) is True
        assert _in_year_range(2019, 2020, 2025) is False
        assert _in_year_range(2026, 2020, 2025) is False

    def test_年份缺失时视为不符合而不是放行(self):
        # 用户明确要"2024 年以后",塞进来一篇年份不详的论文是在违背他的约束
        assert _in_year_range(None, 2024, None) is False
        # 但没有年份约束时,年份不详不影响
        assert _in_year_range(None, None, None) is True


def atom_entry(body: str) -> ET.Element:
    """拼一个 arXiv 风格的 Atom 条目(带命名空间,和真实响应一致)。"""
    return ET.fromstring(
        '<entry xmlns="http://www.w3.org/2005/Atom" '
        'xmlns:arxiv="http://arxiv.org/schemas/atom">' + body + "</entry>"
    )


ARXIV_ENTRY = atom_entry("""
  <id>http://arxiv.org/abs/2502.19271v2</id>
  <published>2025-02-26T00:00:00Z</published>
  <title>Multi-view  Contrastive
     Learning for Recommendation</title>
  <summary>An abstract from arXiv.</summary>
  <author><name>Alice</name></author>
  <author><name>Bob</name></author>
  <arxiv:journal_ref>NeurIPS 2025</arxiv:journal_ref>
""")


class TestArxivMapping:
    def test_字段映射完整(self):
        paper = arxiv_to_paper(ARXIV_ENTRY)

        assert paper["source"] == "arxiv"
        assert paper["title"] == "Multi-view Contrastive Learning for Recommendation"
        assert paper["authors"] == ["Alice", "Bob"]
        assert paper["abstractText"] == "An abstract from arXiv."
        assert paper["publicationYear"] == 2025
        assert paper["venue"] == "NeurIPS 2025"
        assert paper["url"] == "http://arxiv.org/abs/2502.19271v2"

    def test_剥掉版本号(self):
        # 同一篇论文的 v1/v2 是同一篇 —— 带版本号落库会把每次修订存成新的一行
        assert arxiv_to_paper(ARXIV_ENTRY)["externalId"] == "2502.19271"

    def test_没有登记_DOI_时为_None(self):
        # 实测只有约一成的论文有正式发表 DOI;没有是常态,不是异常
        assert arxiv_to_paper(ARXIV_ENTRY)["doi"] is None

    def test_登记了_DOI_就被归一(self):
        entry = atom_entry('<id>http://arxiv.org/abs/2502.00001v1</id>'
                           '<published>2025-01-01T00:00:00Z</published>'
                           '<title>T</title>'
                           '<arxiv:doi>10.1145/ABCD.1234</arxiv:doi>')
        assert arxiv_to_paper(entry)["doi"] == "10.1145/abcd.1234"

    def test_没有_id_则丢弃(self):
        assert arxiv_to_paper(atom_entry("<title>T</title>")) is None

    def test_缺发表日期时年份为_None(self):
        entry = atom_entry('<id>http://arxiv.org/abs/2502.00001v1</id><title>T</title>')
        assert arxiv_to_paper(entry)["publicationYear"] is None

    def test_缺标题时给占位(self):
        entry = atom_entry('<id>http://arxiv.org/abs/2502.00001v1</id>'
                           '<published>2025-01-01T00:00:00Z</published>')
        assert arxiv_to_paper(entry)["title"] == "(无标题)"

    def test_没有引用数(self):
        # arXiv 不提供引用数 —— 如实留 0,不要去别处瞎猜
        assert arxiv_to_paper(ARXIV_ENTRY)["citationCount"] == 0


def paper(source, external_id, title, doi=None):
    return {"source": source, "externalId": external_id, "title": title, "doi": doi}


class TestInterleave:
    def test_各源轮流取一条(self):
        outcomes = [
            ("A", [paper("a", "1", "A1"), paper("a", "2", "A2")]),
            ("B", [paper("b", "1", "B1"), paper("b", "2", "B2")]),
        ]
        labels, merged = _interleave(outcomes, limit=4)

        assert [p["title"] for p in merged] == ["A1", "B1", "A2", "B2"]
        assert labels == ["A", "B"]

    def test_只有部分源有结果时标签只列贡献了的(self):
        outcomes = [("A", [paper("a", "1", "A1")]), ("B", [])]
        labels, merged = _interleave(outcomes, limit=5)

        assert labels == ["A"]
        assert len(merged) == 1

    def test_同一来源同一外部_ID_不重复(self):
        outcomes = [("A", [paper("a", "1", "第一次"), paper("a", "1", "重复")])]
        _, merged = _interleave(outcomes, limit=5)

        assert [p["title"] for p in merged] == ["第一次"]

    def test_同一_DOI_跨源不重复(self):
        # 这就是跨源合并:同一篇论文先从 Crossref 进、又从别处进,只留一条
        outcomes = [
            ("A", [paper("crossref", "10.1/x", "来自 A", doi="10.1/x")]),
            ("B", [paper("arxiv", "2501.1", "来自 B", doi="10.1/x")]),
        ]
        _, merged = _interleave(outcomes, limit=5)

        assert len(merged) == 1

    def test_不同_DOI_的同名论文不合并(self):
        # 同名论文真实存在 —— 只按标题猜会把两篇不同的论文合成一篇
        outcomes = [
            ("A", [paper("crossref", "10.1/x", "同名的论文", doi="10.1/x")]),
            ("B", [paper("crossref", "10.1/y", "同名的论文", doi="10.1/y")]),
        ]
        _, merged = _interleave(outcomes, limit=5)

        assert len(merged) == 2

    def test_达到条数上限就停(self):
        outcomes = [
            ("A", [paper("a", str(i), f"A{i}") for i in range(5)]),
            ("B", [paper("b", str(i), f"B{i}") for i in range(5)]),
        ]
        _, merged = _interleave(outcomes, limit=3)

        assert [p["title"] for p in merged] == ["A0", "B0", "A1"]

    def test_全是空的(self):
        labels, merged = _interleave([("A", []), ("B", [])], limit=5)

        assert labels == []
        assert merged == []


class TestCandidateKey:
    def test_有_DOI_时用_DOI(self):
        # 同一篇从不同源、经由不同检索词捞上来时,DOI 是唯一认得出它的东西
        a = _candidate_key({"source": "crossref", "externalId": "10.1/x", "doi": "10.1/x"})
        b = _candidate_key({"source": "arxiv", "externalId": "2501.1", "doi": "10.1/x"})
        assert a == b

    def test_没有_DOI_时退回来源加外部_ID(self):
        assert _candidate_key({"source": "arxiv", "externalId": "2501.1", "doi": None}) == (
            "id:arxiv:2501.1"
        )


class TestFindCandidates:
    """候选取回与合并。用 monkeypatch 换掉真正的检索,不联网。"""

    def fake_search(self, monkeypatch, mapping):
        """按关键词返回预设结果;映射里没有的关键词当作检索失败。"""

        def search(keywords, limit, year_from, year_to):
            if keywords not in mapping:
                raise RuntimeError(f"{keywords} 失败")
            label, papers = mapping[keywords]
            return SearchOutcome(label, papers)

        monkeypatch.setattr(sources, "search_papers", search)

    def test_同一篇被多路命中时累积标签(self, monkeypatch):
        shared = {"source": "crossref", "externalId": "10.1/x", "title": "共同的一篇", "doi": "10.1/x"}
        self.fake_search(monkeypatch, {
            "a": ("Crossref", [dict(shared)]),
            "b": ("Crossref", [dict(shared)]),
        })

        labels, papers = sources.find_candidates([("t1", "a"), ("t2", "b")], per_query=5)

        assert len(papers) == 1
        assert papers[0]["matchedTags"] == ["t1", "t2"]
        assert labels == ["Crossref"]

    def test_某一路失败不影响其余(self, monkeypatch):
        self.fake_search(monkeypatch, {
            "a": ("Crossref", [{"source": "crossref", "externalId": "10.1/x",
                                "title": "甲", "doi": "10.1/x"}]),
            # "b" 不在映射里 → 抛异常
        })

        labels, papers = sources.find_candidates([("t1", "a"), ("t2", "b")], per_query=5)

        assert [p["title"] for p in papers] == ["甲"]
        assert labels == ["Crossref"]

    def test_每一路都失败时抛出而不是当作没有候选(self, monkeypatch):
        # "上游不可用"和"没有候选"要分得开 —— 前者该返回 503,后者是正常结果
        self.fake_search(monkeypatch, {})

        with pytest.raises(AllSourcesFailed):
            sources.find_candidates([("t1", "a")], per_query=5)

    def test_候选为空但没报错时正常返回(self, monkeypatch):
        self.fake_search(monkeypatch, {"a": ("Crossref", [])})

        labels, papers = sources.find_candidates([("t1", "a")], per_query=5)

        assert papers == []
        assert labels == ["Crossref"]

    def test_多源标签按出现顺序去重(self, monkeypatch):
        self.fake_search(monkeypatch, {
            "a": ("Semantic Scholar、arXiv", [{"source": "arxiv", "externalId": "1",
                                               "title": "甲", "doi": None}]),
            "b": ("arXiv、Crossref", [{"source": "crossref", "externalId": "2",
                                       "title": "乙", "doi": None}]),
        })

        labels, _ = sources.find_candidates([("t1", "a"), ("t2", "b")], per_query=5)

        assert labels == ["Semantic Scholar", "arXiv", "Crossref"]

    def test_空查询列表(self):
        assert sources.find_candidates([], per_query=5) == ([], [])


class TestCleanText:
    def test_空值返回_None(self):
        # 空串进库比 null 更麻烦,统一成 None
        assert _clean_text(None) is None
        assert _clean_text("") is None

    def test_去掉_JATS_标签(self):
        raw = "<jats:p>Context-aware systems</jats:p><jats:p>integrate signals.</jats:p>"
        assert _clean_text(raw) == "Context-aware systems integrate signals."

    def test_解开两层转义(self):
        # Crossref 有些记录被转义了两层:&lt;p&gt; 要先还原成 <p> 再去标签
        assert _clean_text("&lt;p&gt;Hello&lt;/p&gt;") == "Hello"

    def test_还原标题里的与号(self):
        # 实测 Crossref 的标题里就有这种:"…Models &amp; iContracts"
        assert _clean_text("Explainable Large Language Models &amp; iContracts") == (
            "Explainable Large Language Models & iContracts"
        )

    def test_压缩多余空白(self):
        assert _clean_text("a\n\n   b\t c ") == "a b c"

    def test_只有标签时返回_None(self):
        assert _clean_text("<jats:p></jats:p>") is None


class TestNonEmpty:
    def test_空串与空白归一成_None(self):
        assert _non_empty("") is None
        assert _non_empty("   ") is None
        assert _non_empty(None) is None

    def test_列表取第一个非空元素(self):
        # Crossref 的 title / container-title 是数组
        assert _non_empty(["Some Title"]) == "Some Title"
        assert _non_empty(["", "Second"]) == "Second"
        assert _non_empty([]) is None

    def test_去除首尾空白(self):
        assert _non_empty("  x  ") == "x"


class TestNormalizeDoi:
    def test_裸串原样保留(self):
        assert normalize_doi("10.1016/j.eswa.2025.128378") == "10.1016/j.eswa.2025.128378"

    def test_转成小写(self):
        # DOI 本身大小写不敏感,不归一会让同一篇论文因写法不同被当成两篇
        assert normalize_doi("10.1000/XYZ") == "10.1000/xyz"

    def test_剥掉各种前缀(self):
        for raw in (
            "https://doi.org/10.1000/xyz",
            "http://doi.org/10.1000/xyz",
            "doi:10.1000/xyz",
            "  https://doi.org/10.1000/xyz  ",
        ):
            assert normalize_doi(raw) == "10.1000/xyz", raw

    def test_形状不对就不认(self):
        # 宁可不认,也别拿一个假身份去把两篇不同的论文合并成一篇
        assert normalize_doi("not-a-doi") is None
        assert normalize_doi("10.1000") is None          # 缺斜杠
        assert normalize_doi("11.1000/xyz") is None      # 前缀不是 10.
        assert normalize_doi("") is None
        assert normalize_doi(None) is None


S2_ITEM = {
    "paperId": "abc123",
    "title": "A Paper",
    "year": 2025,
    "authors": [{"name": "Alice"}, {"name": "Bob"}],
    "abstract": "Abstract text",
    "url": "https://example.com/a",
    "citationCount": 7,
    "venue": "NeurIPS",
    "externalIds": {"DOI": "10.1000/XYZ", "ArXiv": "2501.00001"},
}

CROSSREF_ITEM = {
    "DOI": "10.1016/j.eswa.2025.128378",
    "title": ["Another Paper"],
    "author": [{"given": "Alice", "family": "Smith"}],
    "issued": {"date-parts": [[2025, 3, 1]]},
    "is-referenced-by-count": 9,
    "abstract": "<jats:p>Hello</jats:p>",
    "URL": "https://doi.org/10.1016/j.eswa.2025.128378",
    "container-title": ["Expert Systems with Applications"],
}


class TestSemanticScholarMapping:
    def test_字段映射完整(self):
        paper = semantic_scholar_to_paper(S2_ITEM)

        assert paper["source"] == "semantic_scholar"
        assert paper["externalId"] == "abc123"
        assert paper["title"] == "A Paper"
        assert paper["authors"] == ["Alice", "Bob"]
        assert paper["abstractText"] == "Abstract text"
        assert paper["publicationYear"] == 2025
        assert paper["venue"] == "NeurIPS"
        assert paper["url"] == "https://example.com/a"
        assert paper["citationCount"] == 7

    def test_DOI_从_externalIds_里取并归一(self):
        # S2 把 DOI 放在 externalIds 里而不是顶层 —— 取错地方的话跨源去重就失效了
        assert semantic_scholar_to_paper(S2_ITEM)["doi"] == "10.1000/xyz"

    def test_没有_DOI_时为_None_而不是报错(self):
        item = {**S2_ITEM, "externalIds": {"ArXiv": "2501.00001"}}
        assert semantic_scholar_to_paper(item)["doi"] is None

    def test_缺少_externalIds_字段也不报错(self):
        item = {key: value for key, value in S2_ITEM.items() if key != "externalIds"}
        assert semantic_scholar_to_paper(item)["doi"] is None

    def test_没有_paperId_则丢弃(self):
        assert semantic_scholar_to_paper({**S2_ITEM, "paperId": None}) is None

    def test_缺少标题时给占位而不是空串(self):
        assert semantic_scholar_to_paper({**S2_ITEM, "title": None})["title"] == "(无标题)"


class TestCrossrefMapping:
    def test_字段映射完整(self):
        paper = crossref_to_paper(CROSSREF_ITEM)

        assert paper["source"] == "crossref"
        assert paper["externalId"] == "10.1016/j.eswa.2025.128378"
        assert paper["title"] == "Another Paper"
        assert paper["authors"] == ["Alice Smith"]
        assert paper["abstractText"] == "Hello"
        assert paper["publicationYear"] == 2025
        assert paper["venue"] == "Expert Systems with Applications"
        assert paper["citationCount"] == 9

    def test_DOI_同时是身份和_doi_字段(self):
        paper = crossref_to_paper(CROSSREF_ITEM)
        assert paper["doi"] == paper["externalId"] == "10.1016/j.eswa.2025.128378"

    def test_没有_DOI_则丢弃(self):
        # Crossref 的身份就是 DOI,没有它既没法落库也没法跨源认人
        assert crossref_to_paper({**CROSSREF_ITEM, "DOI": None}) is None

    def test_缺少发表日期时年份为_None(self):
        item = {key: value for key, value in CROSSREF_ITEM.items() if key != "issued"}
        assert crossref_to_paper(item)["publicationYear"] is None

    def test_标题里的_HTML_转义被还原(self):
        # 不还原的话,界面上会原样显示 "&amp;"
        item = {**CROSSREF_ITEM, "title": ["Models &amp; iContracts"]}
        assert crossref_to_paper(item)["title"] == "Models & iContracts"
