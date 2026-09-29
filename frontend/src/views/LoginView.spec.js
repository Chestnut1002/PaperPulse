import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'

import LoginView from './LoginView.vue'
import { login } from '../api/auth'
import { authStore } from '../stores/auth'
import { flush, waitFor } from '../test/flush'

vi.mock('../api/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
  fetchCurrentUser: vi.fn(),
}))

async function mountLogin() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { render: () => h('div', 'home') } },
      { path: '/login', name: 'login', component: LoginView },
      { path: '/register', name: 'register', component: { render: () => h('div', 'register') } },
    ],
  })
  const host = document.createElement('div')
  document.body.appendChild(host)

  const app = createApp({ render: () => h(RouterView) })
  app.use(router)
  await router.push('/login')
  await router.isReady()
  app.mount(host)
  await flush()

  return { host, router }
}

function setInput(input, value) {
  input.value = value
  input.dispatchEvent(new Event('input', { bubbles: true }))
}

describe('LoginView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    authStore.signOut()
  })

  it('渲染品牌、标题、两个输入框与切换链接', async () => {
    const { host } = await mountLogin()

    expect(host.textContent).toContain('PaperPulse')
    expect(host.textContent).toContain('欢迎回来')
    expect(host.querySelectorAll('input')).toHaveLength(2)
    expect(host.textContent).toContain('去注册')
  })

  it('提交成功后写入登录态并跳转', async () => {
    login.mockResolvedValue({
      token: 'jwt-abc',
      tokenType: 'Bearer',
      expiresIn: 86400,
      user: { id: 1, username: 'chestnut' },
    })

    const { host, router } = await mountLogin()

    const [username, password] = host.querySelectorAll('input')
    setInput(username, 'chestnut')
    setInput(password, 'secret123')
    await flush()

    host.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    await flush()

    expect(login).toHaveBeenCalledWith({ username: 'chestnut', password: 'secret123' })
    expect(authStore.isLoggedIn).toBe(true)
    expect(router.currentRoute.value.name).toBe('home')
  })

  it('凭据错误时在表单顶部显示错误条,而不是浮层', async () => {
    login.mockRejectedValue(
      Object.assign(new Error('用户名或密码错误'), { status: 401, fieldErrors: null }),
    )

    const { host } = await mountLogin()

    const [username, password] = host.querySelectorAll('input')
    setInput(username, 'chestnut')
    setInput(password, 'wrong')
    await flush()

    host.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    await flush()

    const alert = host.querySelector('.alert')
    expect(alert).not.toBeNull()
    expect(alert.textContent).toContain('用户名或密码错误')
    expect(authStore.isLoggedIn).toBe(false)
  })

  it('字段级错误贴到对应输入框,而不是笼统提示', async () => {
    login.mockRejectedValue(
      Object.assign(new Error('参数校验失败'), {
        status: 400,
        fieldErrors: { username: '用户名不能为空' },
      }),
    )

    const { host } = await mountLogin()

    const [username, password] = host.querySelectorAll('input')
    setInput(username, 'x')
    setInput(password, 'y')
    await flush()

    host.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))

    // Element Plus 对错误提示的显示做了 100ms 防抖,必须等它
    const error = await waitFor(() => host.querySelector('.el-form-item__error'))
    expect(error.textContent).toContain('用户名不能为空')
  })
})
