import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'

import RecommendView from './RecommendView.vue'
import { fetchMyInterests } from '../api/interests'
import { addFavorite, fetchFavorites, recordRead, removeFavorite } from '../api/library'
import { fetchRecommendations } from '../api/recommendations'
import { flush, waitFor } from '../test/flush'

vi.mock('../api/recommendations', () => ({ fetchRecommendations: vi.fn() }))
vi.mock('../api/interests', () => ({ fetchMyInterests: vi.fn() }))
vi.mock('../api/library', () => ({
  fetchFavorites: vi.fn(),
  addFavorite: vi.fn(),
  removeFavorite: vi.fn(),
  recordRead: vi.fn(),
}))

const PAPER = {
  id: 11,
  sourceDisplayName: 'Crossref',
  title: '对比学习用于推荐系统',
  authors: ['Alice', 'Bob'],
  venue: 'NeurIPS',
  publicationYear: 2025,
  url: 'https://example.com/a',
}

function recommendation(paper = PAPER, reason = '命中你的兴趣「推荐系统」(权重 5) · 2025 年') {
  return { paper, score: 14, reason, matchedTags: ['recommender_system'] }
}

function response(overrides = {}) {
  return {
    sourceLabel: 'arXiv、Crossref',
    diversityRatio: 0.3,
    hint: null,
    recommendations: [recommendation()],
    ...overrides,
  }
}

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: RecommendView },
      { path: '/interests', name: 'interests', component: { render: () => h('div', 'interests') } },
      { path: '/account', name: 'account', component: { render: () => h('div', 'account') } },
      // 精读入口对每篇论文都渲染,路由表里少了它,router-link 解析时会炸
      { path: '/papers/:paperId/reading', name: 'reading', component: { render: () => h('div', 'reading') } },
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

const INTERESTS = [
  { tag: 'recommender_system', displayName: '推荐系统', category: '信息检索与推荐', weight: 5 },
  { tag: 'large_language_model', displayName: '大语言模型', category: '人工智能', weight: 4 },
]

describe('RecommendView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    fetchRecommendations.mockResolvedValue(response())
    fetchMyInterests.mockResolvedValue(INTERESTS)
    fetchFavorites.mockResolvedValue([])
    addFavorite.mockResolvedValue({})
    removeFavorite.mockResolvedValue(undefined)
    recordRead.mockResolvedValue({})
  })

  it('加载中说明在等什么,而不是只转圈', async () => {
    let release
    fetchRecommendations.mockReturnValue(new Promise((resolve) => { release = resolve }))

    const { host } = await mountView()

    expect(host.querySelector('.waiting').textContent).toContain('正在按你的兴趣找论文')
    release(response())
  })

  it('展示推荐列表与推荐理由', async () => {
    const { host } = await mountView()

    expect(host.querySelector('.paper-row__title').textContent).toContain('对比学习用于推荐系统')
    // 推荐理由是这一页最该被看见的东西
    expect(host.querySelector('.reason').textContent).toContain('命中你的兴趣「推荐系统」')
    expect(host.querySelector('.paper-row__meta').textContent).toContain('NeurIPS')
  })

  it('没有 arXiv 编号的推荐也给入口,文案是「找可读版本」', async () => {
    fetchRecommendations.mockResolvedValue(response({
      recommendations: [
        recommendation({ ...PAPER, arxivId: '2502.19271' }),
        recommendation({ ...PAPER, id: 12, title: '第二篇' }),
      ],
    }))

    const { host } = await mountView()

    const labels = Array.from(host.querySelectorAll('.paper-row__actions a.link'))
      .map((node) => node.textContent.trim())
    expect(labels).toEqual(['精读', '找可读版本'])
  })

  it('顶部说明基于几个兴趣标签、候选来自哪里', async () => {
    const { host } = await mountView()

    const head = host.querySelector('.page-head p').textContent
    expect(head).toContain('2 个兴趣标签')
    expect(head).toContain('核心方向 2 个')   // 权重 ≥ 4 的有两个
    expect(head).toContain('arXiv、Crossref')
  })

  it('没选兴趣标签时给出引导,而不是空白页', async () => {
    fetchRecommendations.mockResolvedValue(response({
      sourceLabel: null,
      hint: '还没有选兴趣标签 —— 先去「兴趣标签」里选几个,推荐才有依据。',
      recommendations: [],
    }))
    fetchMyInterests.mockResolvedValue([])

    const { host } = await mountView()

    expect(host.querySelector('.guide').textContent).toContain('还没有选兴趣标签')
    expect(host.querySelector('.guide a').getAttribute('href')).toBe('/interests')
  })

  it('有候选但一篇都没有时给可操作的建议', async () => {
    fetchRecommendations.mockResolvedValue(response({ recommendations: [] }))

    const { host } = await mountView()

    expect(host.textContent).toContain('这次没找到合适的候选')
  })

  it('收藏与取消收藏', async () => {
    const { host } = await mountView()

    const button = host.querySelector('.paper-row__actions .el-button')
    expect(button.textContent).toContain('收藏')

    button.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()
    expect(addFavorite).toHaveBeenCalledWith(11)
    expect(host.querySelector('.paper-row__actions .el-button').textContent).toContain('已收藏')

    host.querySelector('.paper-row__actions .el-button')
      .dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()
    expect(removeFavorite).toHaveBeenCalledWith(11)
  })

  it('点开链接时记一次阅读', async () => {
    const { host } = await mountView()

    host.querySelector('.paper-row__title').dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()

    expect(recordRead).toHaveBeenCalledWith(11)
  })

  it('加载失败时给出提示而不是白屏', async () => {
    fetchRecommendations.mockRejectedValue(
      Object.assign(new Error('推荐服务不可用,请确认 ai-service 已启动'), { status: 503 }),
    )

    const { host } = await mountView()

    const alert = await waitFor(() => host.querySelector('.alert'))
    expect(alert.textContent).toContain('推荐服务不可用')
  })

  it('侧栏列出兴趣标签,并可跳到管理页', async () => {
    const { host } = await mountView()

    const tags = Array.from(host.querySelectorAll('.side .tag')).map((node) => node.textContent.trim())
    expect(tags).toEqual(['推荐系统', '大语言模型'])
    expect(host.querySelector('.side a').getAttribute('href')).toBe('/interests')
  })
})
