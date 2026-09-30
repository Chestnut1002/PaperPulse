"""评测里用到的数据结构。"""

from dataclasses import dataclass


@dataclass(frozen=True)
class Candidate:
    """候选池里的一篇论文。"""

    id: str
    title: str
    year: int | None
    cited_by: int
    topics: tuple[str, ...]


@dataclass(frozen=True)
class EvalUser:
    """一个"用户"。

    这里的一个"用户"**是一篇论文**:它的参考文献就是一次完整的、时间上收敛的引用行为。
    比"用户 = 作者"更小更干净 —— 作者的引用分散在多年、多个主题上,噪声大得多。

    @param history     较早引用的那些(已知偏好)
    @param relevant    较晚引用的那些的 id —— **标准答案**,注意它们不在 history 里
    @param interests   从 history 推出来的兴趣关键词与权重(1~5)
    @param cutoff_year 时间切分的分界年。**新近度要以它为基准,而不是今天的年份** ——
                       这是历史数据,拿今天去算会让所有论文都"很旧",把新近度信号压平
    """

    user_id: str
    history: tuple[Candidate, ...]
    relevant: frozenset[str]
    interests: tuple[tuple[str, int], ...]
    cutoff_year: int
