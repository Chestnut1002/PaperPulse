import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'

import AppLayout from './AppLayout.vue'
import { authStore } from '../stores/auth'
import { flush } from '../test/flush'

vi.mock('../api/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
  fetchCurrentUser: vi.fn(),
}))

async function mountLayout() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: { render: () => h('div', 'login') } },
      {
        path: '/',
        component: AppLayout,
        children: [
          { path: '', name: 'home', component: { render: () => h('div', 'home') } },
          { path: 'interests', name: 'interests', component: { render: () => h('div', 'interests') } },
        ],
      },
    ],
  })
  const host = document.createElement('div')
  document.body.appendChild(host)

  const app = createApp({ render: () => h(RouterView) })
  app.use(router)
  await router.push('/')
  await router.isReady()
  app.mount(host)
  await flush()

  return { host, router }
}

describe('AppLayout', () => {
  beforeEach(() => {
    localStorage.clear()
    authStore.signOut()
    authStore.signIn({ token: 'jwt-abc', user: { id: 1, username: 'chestnut' } })
  })

  it('显示品牌、当前用户与首字母头像', async () => {
    const { host } = await mountLayout()

    expect(host.querySelector('.brand').textContent).toContain('PaperPulse')
    expect(host.querySelector('.shell__name').textContent).toBe('chestnut')
    expect(host.querySelector('.avatar').textContent).toBe('c')
  })

  it('导航项来自路由表,并高亮当前页', async () => {
    const { host, router } = await mountLayout()

    const links = Array.from(host.querySelectorAll('.shell__navlink'))
    expect(links.map((link) => link.textContent.trim())).toEqual(['首页', '兴趣标签'])
    expect(links[0].classList.contains('router-link-exact-active')).toBe(true)

    await router.push('/interests')
    await flush()

    const after = Array.from(host.querySelectorAll('.shell__navlink'))
    expect(after[1].classList.contains('router-link-exact-active')).toBe(true)
    expect(after[0].classList.contains('router-link-exact-active')).toBe(false)
  })

  it('退出登录清空登录态并回到登录页', async () => {
    const { host, router } = await mountLayout()

    host.querySelector('.linkbtn').dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()

    expect(authStore.isLoggedIn).toBe(false)
    expect(localStorage.getItem('paperpulse.auth')).toBeNull()
    expect(router.currentRoute.value.name).toBe('login')
  })
})
