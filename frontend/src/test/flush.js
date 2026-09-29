import { nextTick } from 'vue'

/**
 * 等异步链彻底落定后返回。
 *
 * 只 await 一两次 nextTick 是不够的:表单提交要走「异步校验 → 请求 → 更新状态 → 重绘」
 * 好几轮,而且中间夹着真正的 promise 回调。这里交替清微任务与宏任务,把链子跑完。
 *
 * 仅供测试使用,不被应用代码引用。
 */
export async function flush(times = 8) {
  for (let index = 0; index < times; index += 1) {
    await nextTick()
    await new Promise((resolve) => setTimeout(resolve, 0))
  }
}

/**
 * 轮询直到取到值,超时则抛错。用于等待带防抖 / 计时的 DOM 变化。
 *
 * 例:Element Plus 把表单错误提示的显示防抖了 100ms(refDebounced(validateState, 100)),
 * 手动使用时察觉不到,但断言 DOM 的测试必须等它。
 */
export async function waitFor(predicate, { timeout = 1000, interval = 20 } = {}) {
  const deadline = Date.now() + timeout
  for (;;) {
    const result = predicate()
    if (result) return result
    if (Date.now() >= deadline) {
      throw new Error(`waitFor 超时(${timeout}ms)`)
    }
    await new Promise((resolve) => setTimeout(resolve, interval))
  }
}
