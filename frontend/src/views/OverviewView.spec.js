import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'

import OverviewView from './OverviewView.vue'
import { fetchMyInterests } from '../api/interests'
import { fetchFavorites, fetchHistory, fetchRatings } from '../api/library'
import { fetchRecommendations } from '../api/recommendations'
import { authStore } from '../stores/auth'
import { flush } from '../test/flush'

vi.mock('../api/interests', () => ({ fetchMyInterests: vi.fn() }))
vi.mock('../api/library', () => ({
  fetchFavorites: vi.fn(),
  fetchHistory: vi.fn(),
  fetchRatings: vi.fn(),
  addFavorite: vi.fn(),
  removeFavorite: vi.fn(),
  recordRead: vi.fn(),
}))
// 首页**不应该**调推荐接口 —— 那正是把它从首页挪开的原因。
// mock 它是为了盯住这一点,不是为了让它真被调用。
vi.mock('../api/recommendations', () => ({ fetchRecommendations: vi.fn() }))

const PAPER = {
  id: 11,
  sourceDisplayName: 'Crossref',
  title: '对比学习用于推荐系统',
  authors: ['Alice'],
  venue: 'NeurIPS',
  publicationYear: 2025,
  url: 'https://example.com/a',
}

function favorite(id, title) {
  return { paper: { ...PAPER, id, title }, favoritedAt: '2026-09-30T10:00:00Z' }
}

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: OverviewView },
      { path: '/search', name: 'search', component: { render: () => h('div', 'search') } },
      { path: '/library', name: 'library', component: { render: () => h('div', 'library') } },
      { path: '/interests', name: 'interests', component: { render: () => h('div', 'interests') } },
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

function setInput(input, value) {
  input.value = value
  input.dispatchEvent(new Event('input', { bubbles: true }))
}

describe('OverviewView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    authStore.signOut()
    authStore.signIn({ token: 'jwt-abc', user: { id: 1, username: 'chestnut' } })

    fetchMyInterests.mockResolvedValue([
      { tag: 'recommender_system', displayName: '推荐系统', category: '信息检索与推荐', weight: 5 },
      { tag: 'information_retrieval', displayName: '信息检索', category: '信息检索与推荐', weight: 2 },
    ])
    fetchFavorites.mockResolvedValue([])
    fetchHistory.mockResolvedValue([])
    fetchRatings.mockResolvedValue([])
  })

  it('首页不调用推荐接口 —— 它是慢的,不该出现在落地页上', async () => {
    await mountView()

    expect(fetchRecommendations).not.toHaveBeenCalled()
  })

  it('欢迎语与搜索框', async () => {
    const { host } = await mountView()

    expect(host.querySelector('.page-head h1').textContent).toContain('chestnut')
    expect(host.querySelector('input')).not.toBeNull()
  })

  it('提交搜索后带着查询跳到检索页', async () => {
    const { host, router } = await mountView()

    setInput(host.querySelector('input'), '对比学习 推荐系统')
    await flush()
    host.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    await flush()

    expect(router.currentRoute.value.name).toBe('search')
    expect(router.currentRoute.value.query.q).toBe('对比学习 推荐系统')
  })

  it('内容过短就不跳转 —— 与检索页的门槛一致', async () => {
    const { host, router } = await mountView()

    setInput(host.querySelector('input'), 'x')
    await flush()
    host.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    await flush()

    expect(router.currentRoute.value.name).toBe('home')
  })

  it('统计卡显示三类行为的数量', async () => {
    fetchFavorites.mockResolvedValue([favorite(1, '甲'), favorite(2, '乙')])
    fetchHistory.mockResolvedValue([{ paper: PAPER, lastReadAt: '2026-09-30T10:00:00Z', readCount: 3 }])
    fetchRatings.mockResolvedValue([])

    const { host } = await mountView()

    const counts = Array.from(host.querySelectorAll('.stat__count')).map((node) => node.textContent)
    expect(counts).toEqual(['2', '1', '0'])
  })

  it('最近收藏最多列三条,且能跳到全部', async () => {
    fetchFavorites.mockResolvedValue([
      favorite(1, '甲'), favorite(2, '乙'), favorite(3, '丙'), favorite(4, '丁'),
    ])

    const { host } = await mountView()

    const titles = Array.from(host.querySelectorAll('.paper-row__title')).map((n) => n.textContent.trim())
    expect(titles).toEqual(['甲', '乙', '丙'])
    expect(host.querySelector('.card__head a').getAttribute('href')).toBe('/library')
  })

  it('没有收藏时给出下一步做什么', async () => {
    const { host } = await mountView()

    expect(host.querySelector('.empty').textContent).toContain('还没有收藏')
  })

  it('侧栏列出兴趣标签与统计', async () => {
    const { host } = await mountView()

    const tags = Array.from(host.querySelectorAll('.side .tag')).map((n) => n.textContent.trim())
    expect(tags).toEqual(['推荐系统', '信息检索'])
    expect(host.querySelector('.side__note').textContent).toContain('2 / 10 已选')
    expect(host.querySelector('.side__note').textContent).toContain('核心方向(权重 ≥ 4)1 个')
  })

  it('加载失败时给出提示而不是白屏', async () => {
    fetchFavorites.mockRejectedValue(Object.assign(new Error('无法连接服务器'), { status: 0 }))

    const { host } = await mountView()

    expect(host.querySelector('.alert').textContent).toContain('无法连接服务器')
  })
})
