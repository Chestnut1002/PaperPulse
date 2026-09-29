import { client } from './client'

// 用户对论文的行为数据:收藏 / 阅读历史 / 评分。
// 三组接口都挂在 /users/me 下,当前用户由 token 决定 —— 路径里不出现用户 id。

/** 收藏列表,论文元数据内嵌,不必再逐条查详情。 */
export const fetchFavorites = () => client.get('/users/me/favorites')

/** 收藏一篇论文。幂等:重复调用返回同一条。 */
export const addFavorite = (paperId) => client.post(`/users/me/favorites/${paperId}`)

/** 取消收藏。没收藏过返回 404。 */
export const removeFavorite = (paperId) => client.delete(`/users/me/favorites/${paperId}`)
