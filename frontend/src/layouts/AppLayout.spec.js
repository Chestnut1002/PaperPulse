import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'

import AppLayout from './AppLayout.vue'
import { navItems } from '../router'
import { authStore } from '../stores/auth'
import { flush } from '../test/flush'

vi.mock('../api/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
  fetchCurrentUser: vi.fn(),
}))

/**
 * 路由从这里推导而不是写死:顶栏导航项本来就来自路由表(`router/index.js` 的 `navItems`),
 * 写死的话每加一个页面这个测试都要跟着改 —— 那不是测试该有的样子。
 * 页面组件用一个占位 div,免得把真实视图的挂载副作用(它们会发请求)带进来。
 */
function buildRoutes() {
  return [
    { path: '/login', name: 'login', component: { render: () => h('div', 'login') } },
    {
      path: '/',
      component: AppLayout,
      children: navItems.map((item, index) => ({
        path: index === 0 ? '' : item.name,
        name: item.name,
        component: { render: () => h('div', item.name) },
      })),
    },
  ]
}

async function mountLayout() {
  const router = createRouter({ history: createMemoryHistory(), routes: buildRoutes() })
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

function navLinks(host) {
  return Array.from(host.querySelectorAll('.shell__navlink'))
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

  it('导航项与路由表一致', async () => {
    const { host } = await mountLayout()

    expect(navLinks(host).map((link) => link.textContent.trim())).toEqual(
      navItems.map((item) => item.label),
    )
  })

  it('高亮当前页,且只有一个被高亮', async () => {
    const { host, router } = await mountLayout()

    // 首页是初始路由
    expect(navLinks(host)[0].classList.contains('router-link-exact-active')).toBe(true)

    const target = navItems[navItems.length - 1]
    await router.push({ name: target.name })
    await flush()

    const active = navLinks(host).filter((link) =>
      link.classList.contains('router-link-exact-active'),
    )
    expect(active).toHaveLength(1)
    expect(active[0].textContent.trim()).toBe(target.label)
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
