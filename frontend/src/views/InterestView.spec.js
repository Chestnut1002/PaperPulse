import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'

import InterestView from './InterestView.vue'
import { fetchCatalog, fetchMyInterests, replaceMyInterests } from '../api/interests'
import { flush } from '../test/flush'

// 真实挂载整个视图:构建通过不等于页面能跑,模板错误与指令解析只有挂载才暴露。
vi.mock('../api/interests', () => ({
  fetchCatalog: vi.fn(),
  fetchMyInterests: vi.fn(),
  replaceMyInterests: vi.fn(),
}))

const CATALOG = {
  minWeight: 1,
  maxWeight: 5,
  maxTags: 3,
  categories: [
    {
      name: '人工智能',
      tags: [
        { key: 'ml', displayName: '机器学习' },
        { key: 'dl', displayName: '深度学习' },
      ],
    },
    {
      name: '信息检索与推荐',
      tags: [
        { key: 'rec', displayName: '推荐系统' },
        { key: 'ir', displayName: '信息检索' },
      ],
    },
  ],
}

/** 挂载真实视图,等 onMounted 里的异步加载落定。 */
async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: InterestView }],
  })
  const host = document.createElement('div')
  document.body.appendChild(host)

  const app = createApp({ render: () => h(RouterView) })
  app.use(router)
  await router.push('/')
  await router.isReady()
  app.mount(host)

  await flush()
  return { app, host }
}

function chips(host) {
  return Array.from(host.querySelectorAll('.tag'))
}

function chipByLabel(host, label) {
  return chips(host).find((chip) => chip.textContent.includes(label))
}

describe('InterestView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    fetchCatalog.mockResolvedValue(CATALOG)
    fetchMyInterests.mockResolvedValue([])
  })

  it('按词表渲染出分类与标签', async () => {
    const { host } = await mountView()

    expect(host.textContent).toContain('人工智能')
    expect(host.textContent).toContain('信息检索与推荐')
    expect(chips(host)).toHaveLength(4)
    expect(host.textContent).toContain('已选 0 / 3')
  })

  it('回显服务端已保存的标签与权重', async () => {
    fetchMyInterests.mockResolvedValue([
      { tag: 'ml', displayName: '机器学习', category: '人工智能', weight: 5 },
    ])

    const { host } = await mountView()

    const chip = chipByLabel(host, '机器学习')
    expect(chip.classList.contains('is-on')).toBe(true)
    expect(chip.dataset.w).toBe('5')
    // 右栏名单里应当出现这个标签
    expect(host.querySelector('.rank').textContent).toContain('机器学习')
    expect(host.textContent).toContain('已选 1 / 3')
  })

  it('点击标签即选中,默认权重 3', async () => {
    const { host } = await mountView()

    chipByLabel(host, '机器学习').dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()

    const chip = chipByLabel(host, '机器学习')
    expect(chip.classList.contains('is-on')).toBe(true)
    expect(chip.dataset.w).toBe('3')
    expect(host.textContent).toContain('已选 1 / 3')
  })

  it('再次点击取消选中', async () => {
    const { host } = await mountView()

    chipByLabel(host, '机器学习').dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()
    chipByLabel(host, '机器学习').dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()

    expect(chipByLabel(host, '机器学习').classList.contains('is-on')).toBe(false)
    expect(host.textContent).toContain('已选 0 / 3')
  })

  it('点标签内第 N 格把权重设为 N', async () => {
    const { host } = await mountView()

    chipByLabel(host, '机器学习').dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()

    const segments = chipByLabel(host, '机器学习').querySelectorAll('.seg')
    expect(segments).toHaveLength(5)
    segments[4].dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()

    expect(chipByLabel(host, '机器学习').dataset.w).toBe('5')
  })

  it('达到上限后第 4 个标签点不动,并给出提示', async () => {
    const { host } = await mountView()

    for (const label of ['机器学习', '深度学习', '推荐系统']) {
      chipByLabel(host, label).dispatchEvent(new MouseEvent('click', { bubbles: true }))
      await flush()
    }
    expect(host.textContent).toContain('已选 3 / 3')

    chipByLabel(host, '信息检索').dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()

    expect(chipByLabel(host, '信息检索').classList.contains('is-on')).toBe(false)
    expect(host.textContent).toContain('已达上限 3 个')
    // 未选中的标签降权,让"点不动"看起来就是点不动
    expect(chipByLabel(host, '信息检索').classList.contains('is-locked')).toBe(true)
  })

  it('没有改动时保存按钮不可用,改动后才可用', async () => {
    const { host } = await mountView()

    const saveButton = host.querySelector('.el-button')
    expect(saveButton.disabled).toBe(true)

    chipByLabel(host, '机器学习').dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()

    expect(host.querySelector('.el-button').disabled).toBe(false)
    expect(host.querySelector('.el-button').textContent).toContain('保存 1 项')
  })

  it('保存提交完整载荷,并用服务端返回的结果重绘', async () => {
    replaceMyInterests.mockResolvedValue([
      { tag: 'ml', displayName: '机器学习', category: '人工智能', weight: 3 },
    ])

    const { host } = await mountView()

    chipByLabel(host, '机器学习').dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()
    host.querySelector('.el-button').dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()

    expect(replaceMyInterests).toHaveBeenCalledWith([{ tag: 'ml', weight: 3 }])
    expect(host.textContent).toContain('已保存')
    expect(host.textContent).not.toContain('有未保存的改动')
  })

  it('清空后再保存:按钮文案变成「清空并保存」,载荷是空数组', async () => {
    fetchMyInterests.mockResolvedValue([
      { tag: 'ml', displayName: '机器学习', category: '人工智能', weight: 3 },
    ])
    replaceMyInterests.mockResolvedValue([])

    const { host } = await mountView()

    const clearButton = Array.from(host.querySelectorAll('.linkbtn')).find((button) =>
      button.textContent.includes('清空'),
    )
    clearButton.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()

    const saveButton = host.querySelector('.el-button')
    expect(saveButton.disabled).toBe(false)
    expect(saveButton.textContent).toContain('清空并保存')

    saveButton.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flush()

    expect(replaceMyInterests).toHaveBeenCalledWith([])
    expect(host.textContent).toContain('已选 0 个')
  })

  it('加载失败时给出提示而不是白屏', async () => {
    fetchCatalog.mockRejectedValue(Object.assign(new Error('无法连接服务器'), { status: 0 }))

    const { host } = await mountView()

    expect(host.textContent).toContain('无法连接服务器')
  })
})
