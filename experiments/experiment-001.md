# experiment-001:检索 Agent 的查询拆解提示词

**日期**:2026-09-29
**对应功能**:REQ-002 检索 Agent 的第一步 —— 把自然语言需求拆成检索参数

---

## 模型

| 项 | 值 |
| ---- | ---- |
| 模型 | DeepSeek(`DEEPSEEK_MODEL`,见 `ai-service/.env`) |
| 接口 | `POST /chat/completions`,OpenAI 兼容协议 |
| 输出约束 | `response_format={"type": "json_object"}` |
| 参数 | `temperature = 0`(抽取任务不是创作任务,要可复现) |

## Prompt 版本

### v1(初版)

```
你是学术文献检索助手。用户会用中文或英文描述他想找的论文,你要把它拆成一次学术数据库检索。

输出一个 json 对象,字段如下:
- keywords:用于检索的英文关键词。学术数据库以英文索引为主,即便用户用中文提问也要译成英文。
  给 3~8 个最能区分主题的词,不要整句话,不要 AND / OR 这类布尔运算符。
- yearFrom:起始年份(整数)。用户表达"近几年""最新的"这类相对时间时,依据给出的当前年份换算成具体年份;
  用户没有限制时间时为 null。
- yearTo:结束年份(整数),通常为 null。
- rationale:一句话说明你为什么这样拆,用中文,给用户看。

只输出 json,不要输出任何解释文字。
```

用户消息里带上当前年份(`当前年份:2026`),让模型能换算"近几年"这类相对时间。

### v2(当前)

改动两处,见下方「问题」。

## 数据集

人工构造的 4 条查询,覆盖中文/英文、有/无时间约束、宽泛/具体:

1. `找 2024 年以后对比学习在推荐系统里的应用`(中文 + 年份下界 + 具体主题)
2. `有没有关于大模型做可解释推荐的最新工作`(中文 + 相对时间)
3. `graph neural network for citation recommendation`(英文 + 无时间约束)
4. `帮我看看扩散模型`(中文 + 极宽泛)

## 结果

### v1 实测

| 输入 | keywords | 年份 |
| ---- | ---- | ---- |
| 1 | `['contrastive learning', 'recommender systems', 'recommendation', 'collaborative filtering', 'self-supervised learning', 'graph neural networks']` | **2025** ~ null |
| 2 | `['large language models', 'explainable recommendation', 'LLM', ...]` | 2024 ~ null |
| 3 | `['graph neural network', 'citation recommendation', ...]` | null |
| 4 | `['diffusion models', 'denoising diffusion probabilistic models', ...]` | null |

### v2 实测

| 输入 | keywords | 年份 |
| ---- | ---- | ---- |
| 1 | `contrastive learning recommender systems` | **2024** ~ null |
| 2 | `large language models explainable recommendation` | 2024 ~ null |
| 3 | `graph neural network citation recommendation` | null |
| 4 | `diffusion models generative deep learning` | null |

## 问题

### 问题一:模型返回的是数组,不是字符串

提示词写的是"给 3~8 个词",模型把它理解成了"返回一个词表",于是 `keywords` 是数组。
而代码里是 `str(data.get("keywords"))` —— Python 对列表做 `str()` 得到的是
`"['contrastive learning', 'recommender systems', ...]"`,**带着方括号和引号的 Python 字面量**。
这个串会被原样送进检索接口,结果必然是垃圾。

**危险之处**:它不会报错。接口照样返回 200,只是搜不到东西或者搜出一堆无关的。
若不把返回结果打印出来看,这个 bug 可以一直藏着。

### 问题二:堆砌同义词反而稀释检索

v1 里模型热心地补了一堆同义词(LLM、denoising diffusion probabilistic models、
score-based generative modeling……)。学术检索接口做的是**相关性排序**而非布尔匹配,
查询串越长、主题越杂,每一篇的相关度打分就越被拉平,前排结果反而更泛。

实测对比:输入 4 在 v1 拿到 7 个词的"关键词汤",v2 收敛成 `diffusion models generative deep learning`。

### 问题三:"2024 年以后"被理解成 2025 起

v1 把"2024 年以后"解析成 `yearFrom = 2025`,理由是"以后 = 2024 之后"。
字面上说得通,但**做文献检索时把边界年排除掉会漏掉结果** —— 用户说"2024 年以后",
绝大多数情况下想看的是 2024 年以来的工作。

## 解决方案

针对三个问题各改一处:

| 问题 | 改法 |
| ---- | ---- |
| 数组形状 | 提示词明确要求"**一个字符串**……不要写成数组或逗号分隔的列表";同时代码里加兜底 —— 是数组就拼成查询串 |
| 同义词堆砌 | 提示词补上"不要堆砌同义词(会把检索结果稀释得又泛又不准)" |
| 年份边界 | 提示词写明"'X 年以后'理解为 **X 年及以后**",并说明理由(排除边界年会漏结果) |

**兜底不能省**:提示词是"请求",不是"保证"。v2 实测模型已经听话了,
但把 `str(list)` 换成会拼串的 `_to_keywords()`,是为了下次换模型/改提示词时不重蹈覆辙。

## 下一步优化

1. **检索质量还没有量化**。目前只验证了"拆解结果看起来对",没有测过召回质量。
   等 REQ-006 离线评测时,可以用同一批查询跑不同的关键词策略,比较 Recall@K —— 那才是有数据的结论。
2. **Semantic Scholar 的 429** 让实测只能走 Crossref。Crossref 的元数据更差
   (preprint 多、摘要缺失率高)。已加上 `SEMANTIC_SCHOLAR_API_KEY` 支持,
   申请到 key 后可以对比两个源的结果质量差异。
3. **没有做多轮检索**。当前是"拆解一次 → 搜一次"。真实检索里常见的
   "先搜、看看结果、再补充检索词"还没有实现,留到后续评估必要性。
