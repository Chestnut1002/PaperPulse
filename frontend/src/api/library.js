import { client } from './client'

// 用户对论文的行为数据:收藏 / 阅读历史 / 评分。
// 三组接口都挂在 /users/me 下,当前用户由 token 决定 —— 路径里不出现用户 id。

/** 收藏列表,论文元数据内嵌,不必再逐条查详情。 */
export const fetchFavorites = () => client.get('/users/me/favorites')

/** 收藏一篇论文。幂等:重复调用返回同一条。 */
export const addFavorite = (paperId) => client.post(`/users/me/favorites/${paperId}`)

/** 取消收藏。没收藏过返回 404。 */
export const removeFavorite = (paperId) => client.delete(`/users/me/favorites/${paperId}`)

/** 阅读历史,最近读的在前。不传条数时后端默认给 100 条(上限 500)。 */
export const fetchHistory = () => client.get('/users/me/history')

/**
 * 记一次阅读。同一篇反复调用是**累加次数**,不是追加记录。
 *
 * <p>用户点开论文链接时调用 —— 那就是"要读它"的意思,不需要再让用户手动标记。
 */
export const recordRead = (paperId) => client.post(`/users/me/history/${paperId}`)

/** 清空阅读历史。已经是空的也返回 204,不会报错。 */
export const clearHistory = () => client.delete('/users/me/history')

/** 评分列表。 */
export const fetchRatings = () => client.get('/users/me/ratings')

/** 打分(1~5)。已有评分则覆盖 —— 一人一篇只有一个分数。 */
export const ratePaper = (paperId, score) =>
  client.put(`/users/me/ratings/${paperId}`, { score })

/** 取消评分。没评过返回 404。 */
export const removeRating = (paperId) => client.delete(`/users/me/ratings/${paperId}`)
