import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'

import SearchView from './SearchView.vue'
import { addFavorite, fetchFavorites, removeFavorite } from '../api/library'
import { searchPapers } from '../api/papers'
import { flush } from '../test/flush'

vi.mock('../api/papers', () => ({ searchPapers: vi.fn() }))
vi.mock('../api/library', () => ({
  fetchFavorites: vi.fn(),
  addFavorite: vi.fn(),
  removeFavorite: vi.fn(),
}))

const PAPER_A = {
  id: 11,
  source: 'crossref',
  sourceDisplayName: 'Crossref',
  externalId: '10.1/a',
  title: '对比学习用于推荐系统',
  authors: ['Alice', 'Bob'],
  abstractText: '一篇关于对比学习的论文。',
  publicationYear: 2025,
  venue: 'NeurIPS',
  url: 'https://example.com/a',
}

const PAPER_B = {
  id: 12,
  source: 'crossref',
  sourceDisplayName: 'Crossref',
  externalId: '10.1/b',
  title: '第二篇',
  authors: [],
  abstractText: null,
  publicationYear: 2024,
  venue: null,
  url: null,
}

function searchResult(papers) {
  return {
    query: '对比学习',
    keywords: 'contrastive learning recommender systems',
    rationale: '拆成对比学习与推荐系统两个主题词',
    sourceLabel: 'Semantic Scholar',
    yearFrom: 2024,
    papers,
  }
}

async function mountView(initialQuery = '') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { render: () => h('div', 'home') } },
      { path: '/search', name: 'search', component: SearchView },
      { path: '/login', name: 'login', component: { render: () => h('div', 'login') } },
    ],
  })
  const host = document.createElement('div')
  document.body.appendChild(host)

  const app = createApp({ render: () => h(RouterView) })
  app.use(router)
  await router.push('/search' + initialQuery)
  await router.isReady()
  app.mount(host)
  await flush()

  return { host, router }
}

function setInput(input, value) {
  input.value = value
  input.dispatchEvent(new Event('input', { bubbles: true }))
}

function submitSearch(host) {
  host.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
}

function paperTitles(host) {
  return Array.from(host.querySelectorAll('.papers .paper__title')).map((node) =>
    node.textContent.trim(),
  )
}

describe('SearchView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    fetchFavorites.mockResolvedValue([])
    addFavorite.mockResolvedValue({})
    removeFavorite.mockResolvedValue(undefined)
  })

  it('还没检索时给出可点的例子', async () => {
    const { host } = await mountView()

    expect(host.querySelectorAll('.hint').length).toBeGreaterThan(0)
    expect(host.textContent).toContain('试试这些')
  })

  it('点例子直接发起检索', async () => {
    searchPapers.mockResolvedValue(searchResult([PAPER_A]))
    const { host } = await mountView()

    host.querySelector('.hint').dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()

    // 只断言视图传了什么:条数的默认值在 api 模块里,不在视图的职责内
    expect(searchPapers).toHaveBeenCalledWith('找 2024 年以后对比学习在推荐系统里的应用')
  })

  it('内容过短时不发请求,直接提示', async () => {
    const { host } = await mountView()

    setInput(host.querySelector('input'), 'x')
    await flush()
    submitSearch(host)
    await flush()

    expect(searchPapers).not.toHaveBeenCalled()
    expect(host.querySelector('.alert').textContent).toContain('至少 2 个字')
  })

  it('显示 Agent 的拆解,让"为什么搜出来的是这些"可见', async () => {
    searchPapers.mockResolvedValue(searchResult([PAPER_A]))
    const { host } = await mountView()

    setInput(host.querySelector('input'), '对比学习')
    await flush()
    submitSearch(host)
    await flush()

    expect(host.querySelector('.plan__keywords').textContent).toBe(
      'contrastive learning recommender systems',
    )
    expect(host.querySelector('.plan__rationale').textContent).toContain('拆成对比学习与推荐系统')
    expect(host.textContent).toContain('数据来自 Semantic Scholar')
    expect(host.textContent).toContain('2024 年至今')
  })

  it('渲染结果列表:标题、作者、期刊、年份、来源', async () => {
    searchPapers.mockResolvedValue(searchResult([PAPER_A, PAPER_B]))
    const { host } = await mountView()

    setInput(host.querySelector('input'), '对比学习')
    await flush()
    submitSearch(host)
    await flush()

    expect(paperTitles(host)).toEqual(['对比学习用于推荐系统', '第二篇'])
    expect(host.textContent).toContain('找到 2 篇')

    const meta = host.querySelectorAll('.paper__meta')[0].textContent
    expect(meta).toContain('Alice, Bob')
    expect(meta).toContain('NeurIPS')
    expect(meta).toContain('2025')
    expect(meta).toContain('Crossref')

    // 作者缺失时不能说成空白
    expect(host.querySelectorAll('.paper__meta')[1].textContent).toContain('作者未提供')
  })

  it('收藏一篇论文', async () => {
    searchPapers.mockResolvedValue(searchResult([PAPER_A]))
    const { host } = await mountView()

    setInput(host.querySelector('input'), '对比学习')
    await flush()
    submitSearch(host)
    await flush()

    const button = host.querySelector('.paper__actions .el-button')
    expect(button.textContent).toContain('收藏')

    button.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()

    expect(addFavorite).toHaveBeenCalledWith(11)
    expect(host.querySelector('.paper__actions .el-button').textContent).toContain('已收藏')
  })

  it('已收藏的论文进来就显示「已收藏」,再点则取消', async () => {
    searchPapers.mockResolvedValue(searchResult([PAPER_A]))
    fetchFavorites.mockResolvedValue([{ paper: PAPER_A, favoritedAt: '2026-09-29T10:00:00Z' }])

    const { host } = await mountView()

    setInput(host.querySelector('input'), '对比学习')
    await flush()
    submitSearch(host)
    await flush()

    const button = host.querySelector('.paper__actions .el-button')
    expect(button.textContent).toContain('已收藏')

    button.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()

    expect(removeFavorite).toHaveBeenCalledWith(11)
    expect(host.querySelector('.paper__actions .el-button').textContent).toContain('收藏')
    expect(host.querySelector('.paper__actions .el-button').textContent).not.toContain('已收藏')
  })

  it('取不到收藏状态不影响检索', async () => {
    fetchFavorites.mockRejectedValue(Object.assign(new Error('炸了'), { status: 500 }))
    searchPapers.mockResolvedValue(searchResult([PAPER_A]))

    const { host } = await mountView()

    setInput(host.querySelector('input'), '对比学习')
    await flush()
    submitSearch(host)
    await flush()

    expect(paperTitles(host)).toEqual(['对比学习用于推荐系统'])
    // 收藏状态取不到,不应弹错误条干扰检索
    expect(host.querySelector('.alert')).toBeNull()
  })

  it('没有结果时给出可操作的建议,而不是一片空白', async () => {
    searchPapers.mockResolvedValue(searchResult([]))
    const { host } = await mountView()

    setInput(host.querySelector('input'), '一个搜不到的词')
    await flush()
    submitSearch(host)
    await flush()

    expect(host.textContent).toContain('找到 0 篇')
    expect(host.textContent).toContain('换个说法')
  })

  it('检索失败时显示错误,并清掉上一次的结果', async () => {
    searchPapers.mockResolvedValueOnce(searchResult([PAPER_A]))
    const { host } = await mountView()

    setInput(host.querySelector('input'), '对比学习')
    await flush()
    submitSearch(host)
    await flush()
    expect(paperTitles(host)).toHaveLength(1)

    searchPapers.mockRejectedValueOnce(
      Object.assign(new Error('检索服务不可用,请确认 ai-service 已启动'), { status: 503 }),
    )
    submitSearch(host)
    await flush()

    expect(host.querySelector('.alert').textContent).toContain('检索服务不可用')
    expect(paperTitles(host)).toHaveLength(0)
  })

  it('地址栏带 q 时自动检索 —— 刷新不丢结果', async () => {
    searchPapers.mockResolvedValue(searchResult([PAPER_A]))

    const { host } = await mountView('?q=对比学习')

    expect(searchPapers).toHaveBeenCalledWith('对比学习')
    expect(paperTitles(host)).toEqual(['对比学习用于推荐系统'])
  })

  it('检索后把查询写进地址栏', async () => {
    searchPapers.mockResolvedValue(searchResult([PAPER_A]))
    const { host, router } = await mountView()

    setInput(host.querySelector('input'), '对比学习')
    await flush()
    submitSearch(host)
    await flush()

    expect(router.currentRoute.value.query.q).toBe('对比学习')
  })
})
