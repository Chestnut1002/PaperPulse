import { beforeEach, describe, expect, it } from 'vitest'

import { useInterestSelection } from './useInterestSelection'

const OPTIONS = { maxTags: 3, minWeight: 1, maxWeight: 5, defaultWeight: 3 }

function newSelection(overrides = {}) {
  return useInterestSelection({ ...OPTIONS, ...overrides })
}

describe('useInterestSelection', () => {
  let selection

  beforeEach(() => {
    selection = newSelection()
  })

  it('初始没有任何标签', () => {
    expect(selection.count.value).toBe(0)
    expect(selection.isFull.value).toBe(false)
    expect(selection.toPayload()).toEqual([])
  })

  it('选中标签时带上默认权重', () => {
    expect(selection.toggle('nlp')).toBe('selected')

    expect(selection.isSelected('nlp')).toBe(true)
    expect(selection.weightOf('nlp')).toBe(3)
    expect(selection.count.value).toBe(1)
  })

  it('再次点击同一个标签即取消', () => {
    selection.toggle('nlp')

    expect(selection.toggle('nlp')).toBe('removed')
    expect(selection.isSelected('nlp')).toBe(false)
    expect(selection.count.value).toBe(0)
  })

  it('达到上限后不再接受新标签,且不改变已有选择', () => {
    selection.toggle('a')
    selection.toggle('b')
    selection.toggle('c')
    expect(selection.isFull.value).toBe(true)

    expect(selection.toggle('d')).toBe('limit-reached')
    expect(selection.count.value).toBe(3)
    expect(selection.isSelected('d')).toBe(false)
  })

  it('到达上限后取消一个,就能再加一个', () => {
    selection.toggle('a')
    selection.toggle('b')
    selection.toggle('c')

    selection.toggle('b')

    expect(selection.toggle('d')).toBe('selected')
    expect(selection.count.value).toBe(3)
  })

  it('上限为 1 时依然可用', () => {
    const single = newSelection({ maxTags: 1 })

    expect(single.toggle('a')).toBe('selected')
    expect(single.toggle('b')).toBe('limit-reached')
  })

  it('可以调整已选标签的权重', () => {
    selection.toggle('nlp')

    expect(selection.setWeight('nlp', 5)).toBe(true)
    expect(selection.weightOf('nlp')).toBe(5)
  })

  it('越界或非整数的权重被拒绝,且不改变原值', () => {
    selection.toggle('nlp')
    selection.setWeight('nlp', 4)

    expect(selection.setWeight('nlp', 0)).toBe(false)
    expect(selection.setWeight('nlp', 6)).toBe(false)
    expect(selection.setWeight('nlp', 2.5)).toBe(false)

    expect(selection.weightOf('nlp')).toBe(4)
  })

  it('对未选中的标签设权重不生效', () => {
    expect(selection.setWeight('nlp', 4)).toBe(false)
    expect(selection.isSelected('nlp')).toBe(false)
  })

  it('replaceAll 完全覆盖当前选择', () => {
    selection.toggle('old')

    selection.replaceAll([
      { tag: 'nlp', weight: 5 },
      { tag: 'cv', weight: 2 },
    ])

    expect(selection.count.value).toBe(2)
    expect(selection.isSelected('old')).toBe(false)
    expect(selection.weightOf('nlp')).toBe(5)
  })

  it('replaceAll 传空数组等于清空(对应后端的"清空兴趣")', () => {
    selection.toggle('nlp')

    selection.replaceAll([])

    expect(selection.count.value).toBe(0)
  })

  it('toPayload 输出 PUT 需要的形状', () => {
    selection.toggle('nlp')
    selection.setWeight('nlp', 5)
    selection.toggle('cv')

    expect(selection.toPayload()).toEqual([
      { tag: 'nlp', weight: 5 },
      { tag: 'cv', weight: 3 },
    ])
  })
})
