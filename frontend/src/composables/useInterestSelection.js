import { computed, reactive } from 'vue'

/**
 * 兴趣标签选择的纯逻辑:选中、权重、数量上限、提交载荷。
 *
 * 刻意与渲染分离 —— 这部分规则(上限、权重区间、重复)全是后端约束的镜像,
 * 出错代价高,所以做成不依赖 DOM 的组合式函数,可以直接单测。
 *
 * @param maxTags       最多可选几个标签,来自词表接口
 * @param minWeight     权重下限,来自词表接口
 * @param maxWeight     权重上限,来自词表接口
 * @param defaultWeight 新选中标签的初始权重
 */
export function useInterestSelection({ maxTags, minWeight, maxWeight, defaultWeight = 3 }) {
  /** tagKey -> weight。用普通对象,提交时再转成数组。 */
  const weights = reactive({})

  const count = computed(() => Object.keys(weights).length)
  const isFull = computed(() => count.value >= maxTags)

  function isSelected(tagKey) {
    return Object.prototype.hasOwnProperty.call(weights, tagKey)
  }

  function weightOf(tagKey) {
    return weights[tagKey]
  }

  /**
   * 点一下标签:没选就选上,选了就取消。
   *
   * @returns {'selected' | 'removed' | 'limit-reached'} 让调用方决定怎么提示 ——
   *          "到达上限"不是异常,但必须让用户知道刚才那下为什么没生效。
   */
  function toggle(tagKey) {
    if (isSelected(tagKey)) {
      delete weights[tagKey]
      return 'removed'
    }
    if (isFull.value) {
      return 'limit-reached'
    }
    weights[tagKey] = defaultWeight
    return 'selected'
  }

  /** 调整已选标签的权重。越界或对未选中的标签调用都不生效,返回 false。 */
  function setWeight(tagKey, weight) {
    if (!isSelected(tagKey)) return false
    if (!Number.isInteger(weight) || weight < minWeight || weight > maxWeight) return false
    weights[tagKey] = weight
    return true
  }

  /** 用服务端返回的列表重置选择(初次加载、保存成功后调用)。 */
  function replaceAll(items) {
    Object.keys(weights).forEach((key) => delete weights[key])
    for (const item of items) {
      weights[item.tag] = item.weight
    }
  }

  /** 提交给 PUT 的请求体。 */
  function toPayload() {
    return Object.entries(weights).map(([tag, weight]) => ({ tag, weight }))
  }

  return { weights, count, isFull, isSelected, weightOf, toggle, setWeight, replaceAll, toPayload }
}
