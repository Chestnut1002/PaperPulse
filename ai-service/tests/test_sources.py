"""检索数据源的纯逻辑:归一化、年份过滤、去重。不联网。"""

from app.sources import _clean_abstract, _dedupe, _in_year_range, _non_empty


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


class TestDedupe:
    def test_按_externalId_去重并保留先出现的(self):
        papers = [
            {"externalId": "a", "title": "第一篇"},
            {"externalId": "b", "title": "第二篇"},
            {"externalId": "a", "title": "重复"},
        ]
        assert [p["title"] for p in _dedupe(papers)] == ["第一篇", "第二篇"]

    def test_空列表(self):
        assert _dedupe([]) == []


class TestCleanAbstract:
    def test_空值返回_None(self):
        # 空串进库比 null 更麻烦,统一成 None
        assert _clean_abstract(None) is None
        assert _clean_abstract("") is None

    def test_去掉_JATS_标签(self):
        raw = "<jats:p>Context-aware systems</jats:p><jats:p>integrate signals.</jats:p>"
        assert _clean_abstract(raw) == "Context-aware systems integrate signals."

    def test_解开两层转义(self):
        # Crossref 有些记录被转义了两层:&lt;p&gt; 要先还原成 <p> 再去标签
        assert _clean_abstract("&lt;p&gt;Hello&lt;/p&gt;") == "Hello"

    def test_压缩多余空白(self):
        assert _clean_abstract("a\n\n   b\t c ") == "a b c"

    def test_只有标签时返回_None(self):
        assert _clean_abstract("<jats:p></jats:p>") is None


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
