"""精读问答的纯逻辑:全文切节、历史截断、引用过滤。**不联网、不调模型。**"""

import pytest

from app.reading.fulltext import FullText, FullTextUnavailable, parse
from app.reading.qa import ContextTooLong, _cited_indexes, _trim_history, _user_prompt

# 手写一份最小的 arXiv 风格 HTML —— 结构照着真实的来(ltx_* 那套 class)
HTML = """
<html><body>
  <nav>导航栏,不该进正文</nav>
  <h1 class="ltx_title ltx_title_document">A Paper About Things</h1>
  <section class="ltx_section">
    <h2 class="ltx_title ltx_title_section">1 Introduction</h2>
    <p class="ltx_p">第一段的正文内容,足够长以通过最小长度检查,写在这里充当真实段落。</p>
    <p class="ltx_p">第二段补充说明,同样要够长,免得被当成空壳节丢掉。</p>
    <section class="ltx_subsection">
      <h3 class="ltx_title ltx_title_subsection">1.1 Background</h3>
      <p class="ltx_p">子节的内容也必须被收进来 —— 按兄弟节点走会漏掉它。</p>
    </section>
  </section>
  <section class="ltx_section">
    <h2 class="ltx_title ltx_title_section">2 Method</h2>
    <p class="ltx_p">方法部分的正文,描述了这个方法大概是怎么做的,略。</p>
  </section>
  <section class="ltx_bibliography">
    <h2 class="ltx_title ltx_title_bibliography">References</h2>
    <p class="ltx_p">[1] 某篇参考文献,不该进正文。</p>
  </section>
  <footer>页脚</footer>
</body></html>
"""


class TestParse:
    def test_切出各节并保留顺序(self):
        full_text = parse("2502.00001", HTML)

        titles = [section.title for section in full_text.sections]
        assert "1 Introduction" in titles
        assert "1.1 Background" in titles
        assert "2 Method" in titles

    def test_子节的内容不会被漏掉(self):
        # arXiv 的节是嵌套的,按兄弟节点走会漏;这条盯着那个坑
        full_text = parse("2502.00001", HTML)
        background = next(s for s in full_text.sections if s.title == "1.1 Background")

        assert "子节的内容" in background.text

    def test_参考文献被排除(self):
        full_text = parse("2502.00001", HTML)

        assert all("References" not in s.title for s in full_text.sections)
        assert "某篇参考文献" not in full_text.to_prompt()

    def test_导航与页脚不进正文(self):
        full_text = parse("2502.00001", HTML)
        joined = full_text.to_prompt()

        assert "导航栏" not in joined
        assert "页脚" not in joined

    def test_节号从1开始且连续(self):
        full_text = parse("2502.00001", HTML)

        assert [s.index for s in full_text.sections] == list(range(1, len(full_text.sections) + 1))

    def test_送进上下文的文本带节号标记(self):
        # 模型靠这些标记回指,没有它们引用就无从谈起
        prompt = parse("2502.00001", HTML).to_prompt()

        assert "[[§1]]" in prompt
        assert "A Paper About Things" in prompt

    def test_没有正文时报错而不是给出空论文(self):
        with pytest.raises(FullTextUnavailable):
            parse("x", "<html><body><nav>空的</nav></body></html>")

    def test_find_按节号取(self):
        full_text = parse("2502.00001", HTML)

        assert full_text.find(1).title == "1 Introduction"
        assert full_text.find(999) is None

    def test_存取回环(self):
        original = parse("2502.00001", HTML)

        restored = FullText.from_dict(original.as_dict())

        assert restored.title == original.title
        assert [s.text for s in restored.sections] == [s.text for s in original.sections]


def full_text_with(section_count: int) -> FullText:
    return FullText(
        arxiv_id="x",
        title="T",
        sections=tuple(
            type("S", (), {"index": i, "title": f"节{i}", "text": "x" * 100})()
            for i in range(1, section_count + 1)
        ),
    )


class TestTrimHistory:
    def test_预算够时全都保留(self):
        turns = [("user", "a" * 10), ("assistant", "b" * 10)]

        kept, omitted = _trim_history(turns, budget_chars=1000)

        assert kept == turns
        assert omitted == 0

    def test_超预算时从最旧的开始丢(self):
        turns = [("user", "a" * 50), ("assistant", "b" * 50), ("user", "c" * 50)]

        kept, omitted = _trim_history(turns, budget_chars=120)

        assert [content[0] for _, content in kept] == ["b", "c"]
        assert omitted == 1

    def test_丢几轮要能数出来(self):
        # 静默截断会让用户以为模型没看到的问题它没看到
        turns = [("user", "a" * 100) for _ in range(5)]

        _, omitted = _trim_history(turns, budget_chars=250)

        assert omitted == 3

    def test_预算为零时全丢(self):
        kept, omitted = _trim_history([("user", "abc")], budget_chars=0)

        assert kept == []
        assert omitted == 1

    def test_保留的顺序仍是时间顺序(self):
        turns = [("user", "a" * 10), ("assistant", "b" * 10)]

        kept, _ = _trim_history(turns, budget_chars=15)

        assert kept == [("assistant", "b" * 10)]


class TestCitedIndexes:
    def test_只保留真实存在的节号(self):
        full_text = full_text_with(3)

        # 模型编出一个不存在的节号是常见的事 —— 丢掉它,而不是猜它想指哪一节
        assert _cited_indexes({"sections": [1, 99, 3]}, full_text) == (1, 3)

    def test_去重并排序(self):
        assert _cited_indexes({"sections": [3, 1, 3]}, full_text_with(5)) == (1, 3)

    def test_不是数组时给空(self):
        assert _cited_indexes({"sections": "1,2"}, full_text_with(5)) == ()
        assert _cited_indexes({}, full_text_with(5)) == ()

    def test_非数字项被跳过(self):
        assert _cited_indexes({"sections": [1, "x", None, 2]}, full_text_with(5)) == (1, 2)


class TestUserPrompt:
    def test_包含原文与提问(self):
        prompt = _user_prompt("论文正文", [], "问题?")

        assert "论文正文" in prompt
        assert "问题?" in prompt

    def test_有历史时带上历史(self):
        prompt = _user_prompt("正文", [("user", "之前问的"), ("assistant", "之前答的")], "新问题")

        assert "之前问的" in prompt
        assert "之前答的" in prompt
        assert "【之前的对话】" in prompt

    def test_没有历史时不出现历史标题(self):
        assert "【之前的对话】" not in _user_prompt("正文", [], "问题")


class TestContextBudget:
    def test_论文本身超预算时明确报错而不是截断论文(self):
        # 截断论文会让回答**静默地**漏掉内容 —— 那比报错糟得多
        huge = FullText(arxiv_id="x", title="T", sections=(
            type("S", (), {"index": 1, "title": "节1", "text": "x" * 100_000})(),
        ))

        with pytest.raises(ContextTooLong):
            from app.reading.qa import ask
            ask(huge, "问题", budget_tokens=100)
