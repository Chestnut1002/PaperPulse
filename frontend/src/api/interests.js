import { client } from './client'

/**
 * 标签词表:6 个分类、34 个标签,并带上 `minWeight / maxWeight / maxTags`。
 * 那三个限制由后端给出而不是前端写死 —— 将来调整时前端不用跟着改。
 */
export const fetchCatalog = () => client.get('/interests')

/** 当前用户的兴趣标签。 */
export const fetchMyInterests = () => client.get('/users/me/interests')

/**
 * 全量替换当前用户的兴趣标签。
 *
 * 语义是"替换"不是"追加":请求体就是提交后的最终状态,要删掉某个标签就不传它。
 * 返回替换后的完整列表(服务端会排序并过滤已下线的标签),界面直接拿它重绘。
 */
export const replaceMyInterests = (items) =>
  client.put('/users/me/interests', { interests: items })
