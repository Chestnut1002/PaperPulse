import { client } from './client'

/**
 * 取当前用户的个性化推荐。
 *
 * <p>一次请求后端要按兴趣标签去外部拉候选、打分、排序,实测 5~10 秒 ——
 * 界面必须给出明确的进行中反馈。
 *
 * @param limit          要几条(后端默认 10,上限 30)
 * @param diversityRatio 探索位比例 0~1(后端默认 0.3)。调大它,列表里"非主要方向"的论文更多
 */
export const fetchRecommendations = (limit, diversityRatio) =>
  client.get('/users/me/recommendations', {
    params: {
      ...(limit ? { limit } : {}),
      ...(diversityRatio === undefined || diversityRatio === null ? {} : { diversityRatio }),
    },
  })
