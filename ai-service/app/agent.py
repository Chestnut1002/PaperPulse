"""检索 Agent:把自然语言需求拆成一次可执行的学术检索。

这一步是整个检索链路的"大脑",但它**只产出检索参数,不产出论文** ——
论文必须来自真实的数据库返回,模型说什么论文存在都不作数。
把边界划在这里,模型就没有编造文献的机会。
"""

from datetime import date

from .llm import LlmError, chat_json
from .schemas import SearchPlan

SYSTEM_PROMPT = """你是学术文献检索助手。用户会用中文或英文描述他想找的论文,你要把它拆成一次学术数据库检索。

输出一个 json 对象,字段如下:
- keywords:用于检索的英文查询串,**一个字符串**。学术数据库以英文索引为主,
  即便用户用中文提问也要译成英文。写成 3~8 个词组成的一个短语,用空格分隔。
  不要写成数组或逗号分隔的列表,不要堆砌同义词(会把检索结果稀释得又泛又不准),
  不要整句话,不要 AND / OR 这类布尔运算符。
- yearFrom:起始年份(整数,**含这一年**)。用户表达"X 年以后""近几年""最新的"这类相对时间时,
  依据给出的当前年份换算成具体年份;"X 年以后"理解为 X 年及以后 —— 做文献检索时把边界年排除掉会漏掉结果。
  用户没有限制时间时为 null。
- yearTo:结束年份(整数,含这一年),通常为 null。
- rationale:一句话说明你为什么这样拆,用中文,给用户看。

只输出 json,不要输出任何解释文字。"""

# 早于这个年份的"论文"基本可以断定是模型写错了
MIN_YEAR = 1900
# 模型偶尔会把 2024 写成 24
_TWO_DIGIT_CUTOFF = 100


def analyze(query: str, today: date | None = None) -> SearchPlan:
    """把自然语言需求拆成检索策略。"""
    current_year = (today or date.today()).year
    data = chat_json(SYSTEM_PROMPT, f"当前年份:{current_year}\n\n用户需求:{query}")
    return to_plan(data, current_year, fallback_keywords=query)


def to_plan(data: dict, current_year: int, fallback_keywords: str) -> SearchPlan:
    """把模型返回的字典规整成 SearchPlan。抽出来是为了能脱离网络单测。"""
    keywords = _to_keywords(data.get("keywords"), fallback_keywords)

    year_from = _normalize_year(data.get("yearFrom"), current_year)
    year_to = _normalize_year(data.get("yearTo"), current_year)

    # 起止反了就交换,而不是报错 —— 用户看到的应当是结果,不是一句参数错误
    if year_from and year_to and year_from > year_to:
        year_from, year_to = year_to, year_from

    return SearchPlan(
        keywords=keywords,
        yearFrom=year_from,
        yearTo=year_to,
        rationale=str(data.get("rationale") or "").strip(),
    )


def _to_keywords(raw: object, fallback_keywords: str) -> str:
    """把模型给的 keywords 规整成一个查询串。

    提示词里明确要求"一个字符串",但实测模型仍会返回关键词数组 —— 而 `str(["a", "b"])`
    得到的是 `"['a', 'b']"`,拿去检索等于废掉。这里对两种形状都做处理。
    """
    if isinstance(raw, list):
        raw = " ".join(str(item).strip() for item in raw if str(item).strip())

    keywords = str(raw or "").strip()
    if keywords:
        return keywords

    # 模型没给出关键词时退回原始查询:检索接口对中文也不是完全无能为力,
    # 有结果总好过因为一次模型抽风就整个失败。
    return fallback_keywords.strip()


def _normalize_year(raw: object, current_year: int) -> int | None:
    """把模型给的年份规整成合法值;实在看不懂就当没给。"""
    if raw is None or raw == "":
        return None
    try:
        year = int(raw)  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return None

    if 0 < year < _TWO_DIGIT_CUTOFF:
        year += 2000

    # 超出合理范围说明模型理解错了,当作"没有约束"比当作"约束到 3025 年"安全
    if year < MIN_YEAR or year > current_year + 1:
        return None
    return year


__all__ = ["analyze", "to_plan", "LlmError"]
