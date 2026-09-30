import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'

import PaperReadingView from './PaperReadingView.vue'
import { askQuestion, fetchPaper } from '../api/reading'
import { flush, waitFor } from '../test/flush'

vi.mock('../api/reading', () => ({ fetchPaper: vi.fn(), askQuestion: vi.fn() }))

const PAPER = {
  id: 11,
  sourceDisplayName: 'arXiv',
  arxivId: '2502.19271',
  title: '对比学习用于推荐系统',
  authors: ['Alice'],
  publicationYear: 2025,
  url: 'https://arxiv.org/abs/2502.19271',
}

const ANSWER = {
  answer: '这篇论文提出了一个多视图方法[[§1]]。\n\n具体而言……[[§3]]',
  citations: [
    { index: 1, title: '1 Introduction', excerpt: '论文正文的开头……' },
    { index: 3, title: '2 Method', excerpt: '方法部分的开头……' },
  ],
  omittedTurns: 0,
}

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/papers/:paperId/reading', component: PaperReadingView }],
  })
  const host = document.createElement('div')
  document.body.appendChild(host)

  const app = createApp({ render: () => h(RouterView) })
  app.use(router)
  await router.push('/papers/11/reading')
  await router.isReady()
  app.mount(host)
  await flush()

  return { host }
}

function ask(host, text) {
  const input = host.querySelector('input')
  input.value = text
  input.dispatchEvent(new Event('input', { bubbles: true }))
  return flush().then(() => {
    host.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    return flush()
  })
}

describe('PaperReadingView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    fetchPaper.mockResolvedValue(PAPER)
    askQuestion.mockResolvedValue(ANSWER)
  })

  it('显示论文标题与元信息', async () => {
    const { host } = await mountView()

    expect(host.querySelector('.page-head h1').textContent).toContain('对比学习用于推荐系统')
    expect(host.textContent).toContain('arXiv')
  })

  it('没有全文的论文说清原因,并且不给输入框', async () => {
    fetchPaper.mockResolvedValue({ ...PAPER, arxivId: null })

    const { host } = await mountView()

    expect(host.querySelector('.unavailable').textContent).toContain('没有可精读的全文')
    // 不给输入框 —— 免得用户白问一场
    expect(host.querySelector('input')).toBeNull()
  })

  it('开始前给几个例子', async () => {
    const { host } = await mountView()

    expect(host.querySelectorAll('.hint').length).toBeGreaterThan(0)
  })

  it('提问后显示回答与依据', async () => {
    const { host } = await mountView()

    await ask(host, '这篇论文提出了什么方法?')

    expect(askQuestion).toHaveBeenCalled()
    expect(host.querySelector('.thread__question').textContent).toContain('提出了什么方法')
    expect(host.querySelector('.thread__answer').textContent).toContain('多视图方法')
    expect(host.querySelectorAll('.source').length).toBe(2)
    expect(host.querySelector('.source__excerpt').textContent).toContain('论文正文的开头')
  })

  it('把 [[§n]] 渲染成引用标记,而不是当 HTML 插进去', async () => {
    const { host } = await mountView()

    await ask(host, '问题一')

    const marks = Array.from(host.querySelectorAll('.thread__answer .cite')).map((n) => n.textContent)
    expect(marks).toEqual(['§1', '§3'])
    // 原始标记不该留在显示文本里
    expect(host.querySelector('.thread__answer').textContent).not.toContain('[[§1]]')
  })

  it('模型输出里的 HTML 不会被当成标签渲染', async () => {
    askQuestion.mockResolvedValue({
      answer: '<img src=x onerror=alert(1)>普通文字',
      citations: [],
      omittedTurns: 0,
    })

    const { host } = await mountView()
    await ask(host, '问题一')

    // 用普通文本插值渲染,标签会原样显示成文字而不是变成元素
    expect(host.querySelector('.thread__answer img')).toBeNull()
    expect(host.querySelector('.thread__answer').textContent).toContain('<img')
  })

  it('被省略的轮次要告诉用户', async () => {
    askQuestion.mockResolvedValue({ ...ANSWER, omittedTurns: 3 })

    const { host } = await mountView()
    await ask(host, '问题一')

    expect(host.querySelector('.thread__note').textContent).toContain('已省略更早的 3 轮')
  })

  it('提问失败时撤回那一轮,并把问题放回输入框', async () => {
    askQuestion.mockRejectedValue(
      Object.assign(new Error('这篇论文没有可精读的全文'), { status: 400 }),
    )

    const { host } = await mountView()
    await ask(host, '问题一')

    // 历史里不该留下一句没有回答的话
    expect(host.querySelector('.thread__question')).toBeNull()
    expect(host.querySelector('input').value).toBe('问题一')

    const alert = await waitFor(() => host.querySelector('.alert'))
    expect(alert.textContent).toContain('没有可精读的全文')
  })

  it('历史只带最近若干轮', async () => {
    const { host } = await mountView()

    for (let index = 1; index <= 6; index += 1) {
      await ask(host, `问题${index}`)
    }

    const [paperId, question, history] = askQuestion.mock.calls.at(-1)

    expect(paperId).toBe('11')
    // 服务端还会按上下文预算再截一遍,但前端不该把整段历史都发过去
    expect(history.length).toBeLessThanOrEqual(8)
    // 当前这一问单独作为 question 发送,不在历史里 —— 否则模型会看到两遍
    expect(question).toBe('问题6')
    expect(history.some((turn) => turn.content === '问题6')).toBe(false)
    expect(history.every((turn) => ['user', 'assistant'].includes(turn.role))).toBe(true)
  })

  it('加载失败时给出提示而不是白屏', async () => {
    fetchPaper.mockRejectedValue(Object.assign(new Error('论文不存在:11'), { status: 404 }))

    const { host } = await mountView()

    expect(host.querySelector('.alert').textContent).toContain('论文不存在')
  })
})
