"""从 OpenAlex 构造评测集。

**没有标准答案就没法评测。** 生产库里只有 3 个真实用户、几乎没有交互,
所以这里用学术关系当代理信号:**一篇论文引用过的论文,就是它"喜欢"的东西**。

一个"用户"是一篇论文,不是一位作者 —— 见 `docs/design/F11-离线评测.md` 第 3.1 节。

抓下来的原始数据会落一份本机缓存:构造一次要发不少请求,调指标时不该重抓。
"""

import json
import random
import time
from dataclasses import dataclass
from pathlib import Path

import httpx

from .. import config
from .models import Candidate, EvalUser

OPENALEX_API = "https://api.openalex.org/works"

# OpenAlex 每次最多接受 50 个 id 的批量过滤
_BATCH_SIZE = 50

# 请求间隔(秒)。OpenAlex 对匿名调用有礼貌额度,别把它打疼
_REQUEST_PAUSE = 0.3

# 种子论文的过滤条件。field 17 = Computer Science —— 评测放在项目关心的领域里才有意义。
# 改动这里要同时改缓存键,否则会读到旧条件下的缓存。
_SEED_FILTER = (
    "has_references:true,primary_topic.field.id:17,"
    "publication_year:2019-2023,type:article"
)


@dataclass(frozen=True)
class BuildConfig:
    """构造评测集的参数。默认值取的是"够跑出稳定结论、又不至于等太久"的折中。"""

    seed_count: int = 40
    """取多少篇种子论文当"用户"。"""

    min_references: int = 8
    """参考文献少于这个数的丢掉 —— 切不出有意义的训练/测试。"""

    negatives_per_user: int = 300
    """每个用户采样多少负例。

    <p>没有负例的话候选池全是相关的,指标会虚高到没有区分度。
    100 个负例配上中位数 24 个测试项,相关项占比会到 22% —— 任务太饱和、指标被压缩。
    提到 300 把它压到个位数百分比,接近常见做法。
    """

    max_keywords: int = 5
    """从历史里推几个兴趣关键词。"""

    history_ratio: float = 0.7
    """按年份切分时,较早的多大比例算历史。"""

    seed: int = 42
    """随机种子。**固定它,否则每次跑出的数字都不一样,没法比较。**"""


def _headers() -> dict:
    return {"User-Agent": config.USER_AGENT}


def _get(client: httpx.Client, params: dict) -> dict:
    response = client.get(OPENALEX_API, params={**params, "mailto": "paperpulse@example.com"})
    if response.status_code != 200:
        raise RuntimeError(f"OpenAlex 返回 HTTP {response.status_code}:{response.text[:200]}")
    time.sleep(_REQUEST_PAUSE)
    return response.json()


def _to_candidate(work: dict) -> Candidate | None:
    """把 OpenAlex 的一条记录变成候选。缺关键字段返回 None。"""
    work_id = work.get("id")
    if not work_id:
        return None
    return Candidate(
        id=work_id,
        title=(work.get("title") or work.get("display_name") or "").strip(),
        year=work.get("publication_year"),
        cited_by=work.get("cited_by_count") or 0,
        # 主题是**受控词表**里的短语,两篇相关论文常常共用同一个 —— 精确匹配因此是有意义的
        topics=tuple(
            topic["display_name"] for topic in (work.get("topics") or []) if topic.get("display_name")
        ),
    )


class OpenAlexSource:
    """带缓存的 OpenAlex 取数。"""

    def __init__(self, cache_dir: Path):
        self.cache_dir = cache_dir
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self.client = httpx.Client(timeout=config.REQUEST_TIMEOUT, headers=_headers(), follow_redirects=True)

    def close(self) -> None:
        self.client.close()

    def _cached(self, key: str, fetch):
        path = self.cache_dir / f"{key}.json"
        if path.exists():
            return json.loads(path.read_text(encoding="utf-8"))
        data = fetch()
        path.write_text(json.dumps(data), encoding="utf-8")
        return data

    def seeds(self, count: int) -> list[dict]:
        """取一批种子论文。

        <p>限定在**计算机领域**(OpenAlex 的 field 17):这个项目关心的是 CS/ML 方向的文献,
        拿核物理论文来评测排序方法虽然也成立,但结论离项目太远。
        还要有引用数据、且年份落在能切出训练/测试的区间里。
        """

        def fetch() -> list[dict]:
            works: list[dict] = []
            cursor = "*"
            while len(works) < count:
                page = _get(self.client, {
                    "filter": _SEED_FILTER,
                    "per-page": 50,
                    "cursor": cursor,
                })
                works.extend(page.get("results") or [])
                cursor = (page.get("meta") or {}).get("next_cursor")
                if not cursor:
                    break
            return works[:count]

        return self._cached(f"seeds-cs-{count}", fetch)

    def works_by_ids(self, ids: list[str]) -> dict[str, dict]:
        """批量取论文。分批是为了少发请求 —— 一次 50 个,不是一次一个。"""

        def fetch() -> dict[str, dict]:
            found: dict[str, dict] = {}
            for start in range(0, len(ids), _BATCH_SIZE):
                batch = ids[start:start + _BATCH_SIZE]
                short = [work_id.rsplit("/", 1)[-1] for work_id in batch]
                page = _get(self.client, {
                    "filter": "openalex_id:" + "|".join(short),
                    "per-page": _BATCH_SIZE,
                })
                for work in page.get("results") or []:
                    found[work["id"]] = work
            return found

        # 缓存键按 id 集合的内容定 —— 同一个集合重跑就不再发请求
        digest = str(abs(hash(tuple(sorted(ids)))))
        return self._cached(f"works-{len(ids)}-{digest}", fetch)


def _interests_from(history: list[Candidate], max_keywords: int) -> tuple[tuple[str, int], ...]:
    """从历史论文的主题里数出最常出现的几个,按排名给权重。

    用 OpenAlex 的主题词表**直接**当兴趣关键词,不映射到我们的 34 标签词表 ——
    少一层有损映射。形式的(若干关键词 + 1~5 权重)与生产一致。
    """
    counts: dict[str, int] = {}
    for candidate in history:
        for topic in candidate.topics:
            counts[topic] = counts.get(topic, 0) + 1

    ranked = sorted(counts.items(), key=lambda pair: (-pair[1], pair[0]))[:max_keywords]
    # 第 1 名权重 5,往下递减,最低 1
    return tuple((topic, max(1, 5 - index)) for index, (topic, _) in enumerate(ranked))


@dataclass(frozen=True)
class UserCase:
    """一次评测的完整输入:一个"用户" + 他要在上面排序的候选池。

    **候选池刻意不含历史项** —— 放进去的话模型会把它们排到前面,白占位置却不计分。
    """

    user: EvalUser
    candidates: tuple[Candidate, ...]


def build_users(source: OpenAlexSource, cfg: BuildConfig) -> list[UserCase]:
    """构造评测集。"""
    rng = random.Random(cfg.seed)

    seeds = source.seeds(cfg.seed_count)
    referenced_ids = [
        work_id
        for seed in seeds
        for work_id in (seed.get("referenced_works") or [])
    ]
    works = source.works_by_ids(sorted(set(referenced_ids)))

    all_candidates = {
        work_id: candidate
        for work_id, work in works.items()
        if (candidate := _to_candidate(work)) is not None
    }
    pool = list(all_candidates.values())

    cases: list[UserCase] = []
    for seed in seeds:
        referenced = [all_candidates.get(work_id) for work_id in (seed.get("referenced_works") or [])]
        # 年份缺失的丢掉 —— 时间切分必须有年份
        referenced = [candidate for candidate in referenced if candidate and candidate.year is not None]
        if len(referenced) < cfg.min_references:
            continue

        referenced.sort(key=lambda candidate: (candidate.year, candidate.id))
        split = min(max(1, int(len(referenced) * cfg.history_ratio)), len(referenced) - 1)

        history, test = referenced[:split], referenced[split:]
        if not history or not test:
            continue

        known = {candidate.id for candidate in referenced}
        negatives = _sample_negatives(pool, known, cfg.negatives_per_user, rng)

        cases.append(UserCase(
            user=EvalUser(
                user_id=seed["id"],
                history=tuple(history),
                relevant=frozenset(candidate.id for candidate in test),
                interests=_interests_from(history, cfg.max_keywords),
                cutoff_year=history[-1].year,
            ),
            candidates=tuple(test) + tuple(negatives),
        ))

    return cases


def _sample_negatives(pool: list[Candidate], exclude: set[str],
                      count: int, rng: random.Random) -> list[Candidate]:
    """从全局池里采样负例,**排除该用户已知的那些**。"""
    choices = [candidate for candidate in pool if candidate.id not in exclude]
    if len(choices) <= count:
        return choices
    return rng.sample(choices, count)
