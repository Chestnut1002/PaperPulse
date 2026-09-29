import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { ElMessageBox } from 'element-plus'

import LibraryView from './LibraryView.vue'
import {
  clearHistory,
  fetchFavorites,
  fetchHistory,
  fetchRatings,
  ratePaper,
  removeFavorite,
  removeRating,
} from '../api/library'
import { flush, waitFor } from '../test/flush'

vi.mock('../api/library', () => ({
  fetchFavorites: vi.fn(),
  fetchHistory: vi.fn(),
  fetchRatings: vi.fn(),
  clearHistory: vi.fn(),
  ratePaper: vi.fn(),
  removeFavorite: vi.fn(),
  removeRating: vi.fn(),
  addFavorite: vi.fn(),
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

const OTHER_PAPER = { ...PAPER, id: 12, title: '第二篇', venue: null, url: null }

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: LibraryView }],
  })
  const host = document.createElement('div')
  document.body.appendChild(host)

  const app = createApp({ render: () => h(RouterView) })
  app.use(router)
  await router.push('/')
  await router.isReady()
  app.mount(host)
  await flush()

  return { host }
}

function tab(host, label) {
  return Array.from(host.querySelectorAll('.tabs__item')).find((node) =>
    node.textContent.includes(label),
  )
}

async function switchTo(host, label) {
  tab(host, label).dispatchEvent(new MouseEvent('click', { bubbles: true }))
  await flush()
}

function titles(host) {
  return Array.from(host.querySelectorAll('.paper-row__title')).map((node) =>
    node.textContent.trim(),
  )
}

function clickButton(host, text) {
  const button = Array.from(host.querySelectorAll('.el-button')).find((node) =>
    node.textContent.includes(text),
  )
  button.dispatchEvent(new MouseEvent('click', { bubbles: true }))
}

describe('LibraryView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.restoreAllMocks()
    fetchFavorites.mockResolvedValue([])
    fetchHistory.mockResolvedValue([])
    fetchRatings.mockResolvedValue([])
    clearHistory.mockResolvedValue(undefined)
    removeFavorite.mockResolvedValue(undefined)
    removeRating.mockResolvedValue(undefined)
    ratePaper.mockResolvedValue({ paper: PAPER, score: 5, ratedAt: '2026-09-30T10:00:00Z' })
  })

  it('三个标签页都显示条数', async () => {
    fetchFavorites.mockResolvedValue([{ paper: PAPER, favoritedAt: '2026-09-29T10:00:00Z' }])
    fetchHistory.mockResolvedValue([
      { paper: PAPER, lastReadAt: '2026-09-29T11:00:00Z', readCount: 3 },
    ])
    fetchRatings.mockResolvedValue([{ paper: PAPER, score: 4, ratedAt: '2026-09-29T12:00:00Z' }])

    const { host } = await mountView()

    expect(tab(host, '收藏').textContent).toContain('1')
    expect(tab(host, '阅读历史').textContent).toContain('1')
    expect(tab(host, '评分').textContent).toContain('1')
  })

  it('默认展示收藏,并带上收藏时间', async () => {
    fetchFavorites.mockResolvedValue([{ paper: PAPER, favoritedAt: '2026-09-29T10:00:00Z' }])

    const { host } = await mountView()

    expect(titles(host)).toEqual(['对比学习用于推荐系统'])
    expect(host.querySelector('.paper-row__meta').textContent).toContain('收藏于')
    expect(host.textContent).toContain('NeurIPS')
  })

  it('三个标签页的空态说的是不同的话', async () => {
    const { host } = await mountView()

    expect(host.querySelector('.empty').textContent).toContain('还没有收藏')

    await switchTo(host, '阅读历史')
    expect(host.querySelector('.empty').textContent).toContain('还没有阅读记录')

    await switchTo(host, '评分')
    expect(host.querySelector('.empty').textContent).toContain('还没有给论文打过分')
  })

  it('阅读历史显示读过的次数', async () => {
    fetchHistory.mockResolvedValue([
      { paper: PAPER, lastReadAt: '2026-09-29T11:00:00Z', readCount: 3 },
    ])

    const { host } = await mountView()
    await switchTo(host, '阅读历史')

    expect(host.querySelector('.paper-row__meta').textContent).toContain('读过 3 次')
  })

  it('取消收藏后从列表里移除', async () => {
    fetchFavorites.mockResolvedValue([{ paper: PAPER, favoritedAt: '2026-09-29T10:00:00Z' }])

    const { host } = await mountView()
    clickButton(host, '取消收藏')
    await flush()

    expect(removeFavorite).toHaveBeenCalledWith(11)
    expect(titles(host)).toEqual([])
    expect(host.querySelector('.empty')).not.toBeNull()
  })

  it('在收藏里评分,该论文随即出现在评分页', async () => {
    fetchFavorites.mockResolvedValue([{ paper: PAPER, favoritedAt: '2026-09-29T10:00:00Z' }])

    const { host } = await mountView()

    // 点第 5 颗星。**用 .el-rate__item 而不是 .el-rate__icon** ——
    // Element Plus 每个星位渲染两个 icon(空星 + 实星),按 icon 取下标会差一倍。
    const stars = host.querySelectorAll('.el-rate__item')
    expect(stars).toHaveLength(5)
    stars[4].dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()

    expect(ratePaper).toHaveBeenCalledWith(11, 5)
    expect(tab(host, '评分').textContent).toContain('1')

    await switchTo(host, '评分')
    expect(titles(host)).toEqual(['对比学习用于推荐系统'])
  })

  it('取消评分只影响那一条', async () => {
    fetchRatings.mockResolvedValue([
      { paper: PAPER, score: 4, ratedAt: '2026-09-29T12:00:00Z' },
      { paper: OTHER_PAPER, score: 2, ratedAt: '2026-09-29T13:00:00Z' },
    ])

    const { host } = await mountView()
    await switchTo(host, '评分')

    clickButton(host, '取消评分')
    await flush()

    expect(removeRating).toHaveBeenCalledWith(11)
    expect(titles(host)).toEqual(['第二篇'])
  })

  it('清空历史要先确认', async () => {
    fetchHistory.mockResolvedValue([
      { paper: PAPER, lastReadAt: '2026-09-29T11:00:00Z', readCount: 1 },
    ])
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')

    const { host } = await mountView()
    await switchTo(host, '阅读历史')

    host.querySelector('.linkbtn').dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()

    expect(confirm).toHaveBeenCalled()
    expect(clearHistory).toHaveBeenCalled()
    expect(titles(host)).toEqual([])
  })

  it('确认框里点取消就什么都不做', async () => {
    fetchHistory.mockResolvedValue([
      { paper: PAPER, lastReadAt: '2026-09-29T11:00:00Z', readCount: 1 },
    ])
    vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel')

    const { host } = await mountView()
    await switchTo(host, '阅读历史')

    host.querySelector('.linkbtn').dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()

    expect(clearHistory).not.toHaveBeenCalled()
    expect(titles(host)).toEqual(['对比学习用于推荐系统'])
  })

  it('加载失败时给出提示而不是白屏', async () => {
    fetchFavorites.mockRejectedValue(Object.assign(new Error('无法连接服务器'), { status: 0 }))

    const { host } = await mountView()

    expect(host.querySelector('.alert').textContent).toContain('无法连接服务器')
  })

  it('取消失败时把原因显示出来', async () => {
    fetchFavorites.mockResolvedValue([{ paper: PAPER, favoritedAt: '2026-09-29T10:00:00Z' }])
    removeFavorite.mockRejectedValue(Object.assign(new Error('论文不存在:11'), { status: 404 }))

    const { host } = await mountView()
    clickButton(host, '取消收藏')

    const alert = await waitFor(() => host.querySelector('.alert'))
    expect(alert.textContent).toContain('论文不存在')
  })
})
