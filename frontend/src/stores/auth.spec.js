import { beforeEach, describe, expect, it, vi } from 'vitest'

const STORAGE_KEY = 'paperpulse.auth'

/**
 * 重新加载模块,拿到一个"刚启动"的 store。
 * 从存储恢复登录态的逻辑只在模块首次求值时执行,不重置模块就测不到。
 */
async function freshStore() {
  vi.resetModules()
  const module = await import('./auth.js')
  return module.authStore
}

describe('authStore', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.resetModules()
  })

  it('没有存储内容时初始为未登录', async () => {
    const store = await freshStore()

    expect(store.isLoggedIn).toBe(false)
    expect(store.token).toBeNull()
    expect(store.user).toBeNull()
  })

  it('signIn 写入状态并持久化', async () => {
    const store = await freshStore()
    store.signIn({ token: 'jwt-abc', user: { id: 1, username: 'chestnut' } })

    expect(store.isLoggedIn).toBe(true)
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY))).toEqual({
      token: 'jwt-abc',
      user: { id: 1, username: 'chestnut' },
    })
  })

  it('模块重新加载时从存储恢复登录态', async () => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ token: 'jwt-abc', user: { id: 1, username: 'chestnut' } }),
    )

    const store = await freshStore()

    expect(store.isLoggedIn).toBe(true)
    expect(store.user.username).toBe('chestnut')
  })

  it('signOut 清空状态与存储', async () => {
    const store = await freshStore()
    store.signIn({ token: 'jwt-abc', user: { id: 1 } })

    store.signOut()

    expect(store.isLoggedIn).toBe(false)
    expect(store.user).toBeNull()
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  it('存储内容损坏时按未登录处理,不抛异常', async () => {
    localStorage.setItem(STORAGE_KEY, '{ 这不是 JSON')

    const store = await freshStore()

    expect(store.isLoggedIn).toBe(false)
  })

  it('setUser 只更新用户信息,不动 token', async () => {
    const store = await freshStore()
    store.signIn({ token: 'jwt-abc', user: { id: 1, username: 'old' } })

    store.setUser({ id: 1, username: 'new' })

    expect(store.token).toBe('jwt-abc')
    expect(store.user.username).toBe('new')
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)).user.username).toBe('new')
  })
})
