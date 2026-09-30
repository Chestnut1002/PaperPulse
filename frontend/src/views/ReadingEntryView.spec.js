import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'

import ReadingEntryView from './ReadingEntryView.vue'
import { openByArxiv } from '../api/reading'
import { flush, waitFor } from '../test/flush'

vi.mock('../api/reading', () => ({ openByArxiv: vi.fn(), fetchPaper: vi.fn(), askQuestion: vi.fn() }))

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/reading', name: 'reading-entry', component: ReadingEntryView },
      { path: '/papers/:paperId/reading', name: 'reading', component: { render: () => h('div', 'paper') } },
    ],
  })
  const host = document.createElement('div')
  document.body.appendChild(host)

  const app = createApp({ render: () => h(RouterView) })
  app.use(router)
  await router.push('/reading')
  await router.isReady()
  app.mount(host)
  await flush()

  return { host, router }
}

async function submit(host, text) {
  const input = host.querySelector('input')
  input.value = text
  input.dispatchEvent(new Event('input', { bubbles: true }))
  await flush()
  host.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
  await flush()
}

describe('ReadingEntryView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    openByArxiv.mockResolvedValue({ id: 42, title: '一篇论文', arxivId: '2502.19271' })
  })

  it('给出输入框与说明', async () => {
    const { host } = await mountView()

    expect(host.querySelector('input')).not.toBeNull()
    expect(host.textContent).toContain('粘贴 arXiv 编号或链接')
  })

  it('说明现在能读哪些、不能读哪些', async () => {
    const { host } = await mountView()

    // 覆盖面是这个功能的真实边界,用户该在动手前就知道
    expect(host.textContent).toContain('能读')
    expect(host.textContent).toContain('不能读')
    expect(host.textContent).toContain('期刊论文')
  })

  it('点例子填进输入框', async () => {
    const { host } = await mountView()

    host.querySelector('.ex').dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()

    expect(host.querySelector('input').value).toBe('2502.19271')
  })

  it('提交后跳到那篇论文的精读页', async () => {
    const { host, router } = await mountView()

    await submit(host, '2502.19271')

    expect(openByArxiv).toHaveBeenCalledWith('2502.19271')
    expect(router.currentRoute.value.name).toBe('reading')
    expect(router.currentRoute.value.params.paperId).toBe('42')
  })

  it('整条链接原样交给后端解析 —— 前端不该自己抠编号', async () => {
    const { host } = await mountView()

    await submit(host, 'https://arxiv.org/abs/2502.19271')

    expect(openByArxiv).toHaveBeenCalledWith('https://arxiv.org/abs/2502.19271')
  })

  it('空内容不发请求', async () => {
    const { host } = await mountView()

    await submit(host, '   ')

    expect(openByArxiv).not.toHaveBeenCalled()
  })

  it('后端认不出编号时把原因显示出来', async () => {
    openByArxiv.mockRejectedValue(
      Object.assign(new Error('没认出 arXiv 编号 —— 可以粘贴编号或 arXiv 的链接'), { status: 400 }),
    )

    const { host, router } = await mountView()
    await submit(host, 'https://example.com/paper')

    const alert = await waitFor(() => host.querySelector('.alert'))
    expect(alert.textContent).toContain('没认出 arXiv 编号')
    // 留在本页,不跳走
    expect(router.currentRoute.value.name).toBe('reading-entry')
  })
})
