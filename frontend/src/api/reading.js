import { client } from './client'

/** 按本地 id 取一篇论文 —— 精读页靠它拿标题与 arXiv 编号,刷新也不丢。 */
export const fetchPaper = (paperId) => client.get(`/papers/${paperId}`)

/**
 * 就一篇论文提问。
 *
 * <p>对话历史由前端带上,后端与 ai-service 都不维持会话状态。
 * **只带最近若干轮** —— 更早的由服务端按上下文预算再截断。
 *
 * @param paperId  本地论文 id
 * @param question 本次提问
 * @param history  [{ role: 'user' | 'assistant', content }]
 */
export const askQuestion = (paperId, question, history = []) =>
  client.post(`/papers/${paperId}/qa`, { question, history })
