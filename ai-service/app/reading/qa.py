"""单篇论文的问答:全文喂给模型,要求它按节引用。

<p><b>不走 RAG。</b>一篇论文去掉参考文献后约 92KB(≈2 万 token),上下文装得下。
RAG 要引入向量库与切块策略,而单论文场景里"全文都在"比"检索得准"更可靠 ——
跨论文检索才需要 RAG。
"""

from dataclasses import dataclass

from .. import config
from ..llm import LlmError, chat_json
from .fulltext import FullText

SYSTEM_PROMPT = """你在帮用户精读一篇论文。用户会就这一篇论文连续提问。

规则:
- **只依据下面给出的论文原文回答。** 原文里没有提到的,就直接说"论文里没有提到",
  不要用你自己的知识补充 —— 用户要的是这篇论文说了什么,不是这个领域一般怎么说。
- 引用原文时用 [[§n]] 标出依据来自哪一节(n 是节号,原文里每节前面都有 [[§n]] 标记)。
- 用中文回答,除非用户用英文提问。
- 输出 json,字段:
  - answer:回答正文,里面有 [[§n]] 标记
  - sections:你用到的节号数组,如 [3, 7];一节都没用到就给空数组 []
只输出 json,不要输出任何解释文字。"""


class ContextTooLong(RuntimeError):
    """论文本身就超出上下文预算。截断论文会让回答**静默地**漏掉内容,所以宁可明说。"""


@dataclass(frozen=True)
class QaResult:
    answer: str
    cited_indexes: tuple[int, ...]
    omitted_turns: int


def ask(full_text: FullText, question: str, history: list[tuple[str, str]] | None = None,
        budget_tokens: int | None = None) -> QaResult:
    """就一篇论文回答一个问题。

    @param history (role, content) 的列表,role 是 "user" 或 "assistant"
    @param budget_tokens 输入上限(估计的 token 数),默认取配置
    """
    turns = history or []
    budget = budget_tokens if budget_tokens is not None else config.QA_CONTEXT_BUDGET

    paper = full_text.to_prompt()
    # 论文与提问是**必须**放进去的,剩下多少才是对话历史能用的
    remaining = budget * config.QA_CHARS_PER_TOKEN - len(SYSTEM_PROMPT) - len(paper) - len(question)
    if remaining <= 0:
        raise ContextTooLong(
            f"这篇论文的正文约 {len(paper) // 1000} 千字符,已经超出上下文预算"
        )

    kept, omitted = _trim_history(turns, remaining)
    data = chat_json(SYSTEM_PROMPT, _user_prompt(paper, kept, question))

    answer = str(data.get("answer") or "").strip()
    if not answer:
        raise LlmError("模型没有给出回答")

    return QaResult(answer=answer, cited_indexes=_cited_indexes(data, full_text),
                    omitted_turns=omitted)


def _trim_history(turns: list[tuple[str, str]],
                  budget_chars: int) -> tuple[list[tuple[str, str]], int]:
    """从**最旧的**轮次开始丢,直到落进预算。

    <p>丢的时候返回丢了几轮 —— 调用方要把这件事告诉用户。**静默截断会让用户以为
    模型没看到的问题它没看到,而实际上是它看到了却装作没看到。**
    """
    kept: list[tuple[str, str]] = []
    left = budget_chars
    for role, content in reversed(turns):
        if len(content) > left:
            break
        kept.append((role, content))
        left -= len(content)
    kept.reverse()
    return kept, len(turns) - len(kept)


def _user_prompt(paper: str, turns: list[tuple[str, str]], question: str) -> str:
    parts = ["【论文原文】", paper, ""]
    if turns:
        parts.append("【之前的对话】")
        for role, content in turns:
            parts.append(("用户:" if role == "user" else "你:") + content)
        parts.append("")
    parts.extend(["【本次提问】", question])
    return "\n".join(parts)


def _cited_indexes(data: dict, full_text: FullText) -> tuple[int, ...]:
    """取出模型回指的节号,**只保留原文里真实存在的那些**。

    <p>模型编出一个不存在的节号是常见的事。丢掉它,而不是去猜它想指哪一节 ——
    猜错就等于制造了一条假引用。
    """
    raw = data.get("sections")
    if not isinstance(raw, list):
        return ()

    indexes: list[int] = []
    for item in raw:
        try:
            index = int(item)
        except (TypeError, ValueError):
            continue
        if index not in indexes and full_text.find(index) is not None:
            indexes.append(index)
    return tuple(sorted(indexes))
