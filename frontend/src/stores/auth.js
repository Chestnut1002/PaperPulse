import { reactive } from 'vue'

/**
 * 登录态:token 与用户信息。
 *
 * <p>持久化到 localStorage,刷新页面不必重新登录 —— 这是 SPA 的默认预期,内存方案一刷新就掉。
 * 代价是 XSS 可读取;本项目没有把用户输入当 HTML 渲染的地方,当前可接受,
 * 将来要收紧可换成后端下发 httpOnly Cookie。
 */
const STORAGE_KEY = 'paperpulse.auth'

function readStored() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    // 存储被禁用、或内容被改坏 —— 按未登录处理,不该因此白屏
    return null
  }
}

const stored = readStored()

export const authStore = reactive({
  token: stored?.token ?? null,
  user: stored?.user ?? null,

  get isLoggedIn() {
    return this.token !== null
  },

  /** 登录成功后写入状态并持久化。入参是 /api/auth/login 的响应体。 */
  signIn(loginResponse) {
    this.token = loginResponse.token
    this.user = loginResponse.user
    persist()
  },

  /** 用服务端返回的最新用户信息刷新本地缓存(如 GET /api/users/me 之后)。 */
  setUser(user) {
    this.user = user
    persist()
  },

  /** 退出登录,以及收到 401 时由请求层调用。 */
  signOut() {
    this.token = null
    this.user = null
    localStorage.removeItem(STORAGE_KEY)
  },
})

function persist() {
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({ token: authStore.token, user: authStore.user }),
  )
}
