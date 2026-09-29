import { beforeEach, describe, expect, it } from 'vitest'

import { ApiError, attachAuthHeader, onResponseError, toApiError } from './client'
import { authStore } from '../stores/auth'

describe('attachAuthHeader', () => {
  beforeEach(() => authStore.signOut())

  it('已登录时注入 Bearer token', () => {
    authStore.signIn({ token: 'jwt-abc', user: { id: 1 } })

    const config = attachAuthHeader({ headers: {} })

    expect(config.headers.Authorization).toBe('Bearer jwt-abc')
  })

  it('未登录时不加 Authorization 头', () => {
    const config = attachAuthHeader({ headers: {} })

    expect(config.headers.Authorization).toBeUndefined()
  })
})

describe('toApiError', () => {
  it('解析后端统一错误体', () => {
    const error = toApiError({
      response: {
        status: 400,
        data: {
          message: '参数校验失败',
          fieldErrors: { username: '用户名只能包含字母、数字和下划线' },
        },
      },
    })

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(400)
    expect(error.message).toBe('参数校验失败')
    expect(error.fieldErrors.username).toContain('字母')
  })

  it('请求没到服务端时给出可读提示,status 为 0', () => {
    const error = toApiError({ message: 'Network Error' })

    expect(error.status).toBe(0)
    expect(error.message).toContain('无法连接服务器')
  })

  it('后端没给 message 时回落到状态码', () => {
    const error = toApiError({ response: { status: 404, data: undefined } })

    expect(error.message).toContain('404')
  })
})

describe('onResponseError', () => {
  beforeEach(() => authStore.signOut())

  it('受保护接口返回 401 时清空登录态', async () => {
    authStore.signIn({ token: 'jwt-abc', user: { id: 1 } })

    await expect(
      onResponseError({
        config: { url: '/users/me' },
        response: { status: 401, data: { message: '未登录或登录凭证无效' } },
      }),
    ).rejects.toBeInstanceOf(ApiError)

    expect(authStore.isLoggedIn).toBe(false)
  })

  it('登录接口的 401(凭据错误)不触发登出', async () => {
    authStore.signIn({ token: 'jwt-abc', user: { id: 1 } })

    await expect(
      onResponseError({
        config: { url: '/auth/login' },
        response: { status: 401, data: { message: '用户名或密码错误' } },
      }),
    ).rejects.toThrow('用户名或密码错误')

    expect(authStore.isLoggedIn).toBe(true)
  })

  it('非 401 错误不动登录态', async () => {
    authStore.signIn({ token: 'jwt-abc', user: { id: 1 } })

    await expect(
      onResponseError({
        config: { url: '/users/me' },
        response: { status: 500, data: { message: '服务器内部错误' } },
      }),
    ).rejects.toBeInstanceOf(ApiError)

    expect(authStore.isLoggedIn).toBe(true)
  })
})
