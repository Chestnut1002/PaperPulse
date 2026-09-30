import { client } from './client'

/** 按本地 id 取一篇论文 —— 精读页靠它拿标题与 arXiv 编号,刷新也不丢。 */
export const fetchPaper = (paperId) => client.get(`/papers/${paperId}`)

/**
 * 粘贴 arXiv 编号或链接,直接打开这篇论文。
 *
 * **解析在后端做** —— 用户贴的可能是编号、链接、或夹着编号的一句话,
 * 这套规则只该有一份,不该前端再写一遍。
 *
 * @param reference 用户粘贴的内容
 */
export const openByArxiv = (reference) => client.post('/papers/from-arxiv', { reference })

/**
 * 给一篇没有 arXiv 编号的论文按标题反查预印本。
 *
 * **认不认由后端决定**(匹配规则只有那一份),前端只负责发起与呈现结果。
 * 找到时后端会把编号写进库里 —— 这篇从此能精读,其他用户也不必再查。
 *
 * 404 表示"arXiv 上没有这篇的预印本",是正常结果之一,不是服务出错。
 */
export const resolveArxiv = (paperId) => client.post(`/papers/${paperId}/arxiv-lookup`)

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
