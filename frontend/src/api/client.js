import axios from 'axios'

import { router } from '../router'
import { authStore } from '../stores/auth'

/** 归一化后的接口错误。视图只需要读 message 与 fieldErrors。 */
export class ApiError extends Error {
  constructor(message, status, fieldErrors = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    /** 校验失败(400)时后端返回的逐字段提示,形如 { username: '用户名只能包含字母、数字和下划线' }。 */
    this.fieldErrors = fieldErrors
  }
}

export const client = axios.create({
  // 相对路径:由 Vite 代理转发到后端(见 vite.config.js),因此不存在跨域
  baseURL: '/api',
  timeout: 15000,
})

/** 请求拦截:带上 token。后端只认 Authorization 头,没有会话 Cookie。 */
export function attachAuthHeader(config) {
  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`
  }
  return config
}

/**
 * 成功响应:直接给出 body。视图里就不必再写一层 .data。
 */
export function unwrapResponse(response) {
  return response.data
}

/**
 * 失败响应归一化。
 *
 * 后端的错误格式是统一的 `{timestamp, status, error, message}`(校验失败多带 fieldErrors),
 * 所以这里只有一条解析路径。没有 response 说明请求根本没到服务端 —— 后端没起或超时。
 */
export function toApiError(error) {
  const response = error.response
  if (!response) {
    return new ApiError('无法连接服务器,请确认后端已启动', 0)
  }
  const body = response.data
  return new ApiError(
    body?.message || `请求失败(${response.status})`,
    response.status,
    body?.fieldErrors,
  )
}

/** 401:凭证已失效(过期、被篡改、用户被删除)。清登录态并回登录页。 */
export function handleUnauthorized() {
  authStore.signOut()
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
}

/**
 * 响应拦截的错误出口。
 *
 * `/auth/**` 的 401 要排除在外:登录接口凭据错误也回 401,但那是"密码错了",
 * 不是"会话过期",不该触发登出跳转 —— 只把消息交给登录页展示。
 */
export function onResponseError(error) {
  const status = error.response?.status
  const isAuthEndpoint = error.config?.url?.startsWith('/auth/')

  if (status === 401 && !isAuthEndpoint) {
    handleUnauthorized()
  }
  return Promise.reject(toApiError(error))
}

client.interceptors.request.use(attachAuthHeader)
client.interceptors.response.use(unwrapResponse, onResponseError)
