import { client } from './client'

/**
 * 用自然语言检索论文。
 *
 * <p>后端会调用检索 Agent 拆解查询、去学术数据库找、把结果落库,再返回带本地 id 的论文 ——
 * 拿到就能直接收藏 / 评分。
 *
 * <p><b>慢是有原因的</b>:一次请求包含"大模型拆解 + 外部数据库往返",实测 10 秒上下。
 * 界面必须给出明确的进行中反馈,不能只让按钮转圈。
 */
export const searchPapers = (query, limit = 5) =>
  client.post('/papers/search', { query, limit })
