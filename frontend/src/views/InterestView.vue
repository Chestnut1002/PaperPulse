<script setup>
import { computed, onMounted, onUnmounted, ref, shallowRef } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'

import { fetchCatalog, fetchMyInterests, replaceMyInterests } from '../api/interests'
import WeightTicks from '../components/WeightTicks.vue'
import { useInterestSelection } from '../composables/useInterestSelection'

const loading = ref(true)
const saving = ref(false)
const loadError = ref('')
const saveError = ref('')
const savedAt = ref('')
/** 点第 11 个标签时短暂变红,让用户知道刚才那下为什么没生效 */
const limitFlash = ref(false)
let flashTimer = null

const catalog = shallowRef(null)
/** 选择引擎。词表返回后才建得出来 —— maxTags / 权重区间都由后端给。 */
const selection = shallowRef(null)
/** 已保存状态的快照,用来判断"有没有未保存的改动" */
const snapshot = ref('')

const categories = computed(() => catalog.value?.categories ?? [])
const maxTags = computed(() => catalog.value?.maxTags ?? 0)
const minWeight = computed(() => catalog.value?.minWeight ?? 1)
const maxWeight = computed(() => catalog.value?.maxWeight ?? 5)

const selectedCount = computed(() => selection.value?.count.value ?? 0)
const isFull = computed(() => selectedCount.value >= maxTags.value)
const coreCount = computed(() =>
  selection.value
    ? Object.values(selection.value.weights).filter((weight) => weight >= 4).length
    : 0,
)
const isDirty = computed(() => selection.value !== null && serialize() !== snapshot.value)

/** tagKey -> 词表顺序,用于排序 */
const vocabularyOrder = computed(() => {
  const map = new Map()
  let index = 0
  for (const category of categories.value) {
    for (const tag of category.tags) {
      map.set(tag.key, index++)
    }
  }
  return map
})

/** 按权重降序排列已选项;同权重按词表顺序 —— 名单要稳定,不能每次渲染都换位。 */
const ranked = computed(() => {
  if (!selection.value) return []
  const order = vocabularyOrder.value
  return Object.entries(selection.value.weights)
    .map(([tag, weight]) => ({ tag, weight, label: labelOf(tag) }))
    .sort((a, b) => b.weight - a.weight || order.get(a.tag) - order.get(b.tag))
})

function labelOf(tagKey) {
  for (const category of categories.value) {
    const found = category.tags.find((tag) => tag.key === tagKey)
    if (found) return found.displayName
  }
  return tagKey
}

function weightOf(tagKey) {
  return selection.value?.weightOf(tagKey) ?? 0
}

function serialize() {
  return JSON.stringify(
    [...selection.value.toPayload()].sort((a, b) => a.tag.localeCompare(b.tag)),
  )
}

function flashLimit() {
  limitFlash.value = true
  clearTimeout(flashTimer)
  flashTimer = setTimeout(() => {
    limitFlash.value = false
  }, 1800)
}

function toggleTag(tagKey) {
  if (selection.value.toggle(tagKey) === 'limit-reached') {
    flashLimit()
  }
}

function setWeight(tagKey, weight) {
  selection.value.setWeight(tagKey, weight)
}

/** 点标签本体 = 切换;点标签内第 N 格 = 把权重设为 N。 */
function onChipClick(tagKey, event) {
  const seg = event.target.closest?.('.seg')
  if (!seg) {
    toggleTag(tagKey)
    return
  }
  const segs = Array.from(event.currentTarget.querySelectorAll('.seg'))
  setWeight(tagKey, segs.indexOf(seg) + 1)
}

function onChipKeydown(event, tagKey) {
  const weight = weightOf(tagKey)
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    toggleTag(tagKey)
  } else if (event.key === 'ArrowRight') {
    event.preventDefault()
    setWeight(tagKey, Math.min(weight + 1, maxWeight.value))
  } else if (event.key === 'ArrowLeft') {
    event.preventDefault()
    setWeight(tagKey, Math.max(weight - 1, minWeight.value))
  }
}

function chipLabel(tag, weight) {
  return weight > 0 ? `${tag.displayName},权重 ${weight} / 5` : `${tag.displayName},未选`
}

function clearAll() {
  selection.value.replaceAll([])
}

async function save() {
  saving.value = true
  saveError.value = ''
  try {
    // 全量替换:请求体就是提交后的最终状态。服务端会排序并过滤已下线的标签,
    // 所以用它的返回值重绘,而不是假定提交什么就是什么。
    const applied = await replaceMyInterests(selection.value.toPayload())
    selection.value.replaceAll(applied)
    snapshot.value = serialize()
    savedAt.value = new Date().toLocaleTimeString()
  } catch (error) {
    saveError.value = error.message
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    const [catalogData, mine] = await Promise.all([fetchCatalog(), fetchMyInterests()])
    catalog.value = catalogData
    selection.value = useInterestSelection({
      maxTags: catalogData.maxTags,
      minWeight: catalogData.minWeight,
      maxWeight: catalogData.maxWeight,
      defaultWeight: 3,
    })
    selection.value.replaceAll(mine)
    snapshot.value = serialize()
  } catch (error) {
    if (error.status !== 401) {
      loadError.value = error.message
    }
  } finally {
    loading.value = false
  }
})

// 上限提示的定时器随组件一起回收,别让它在页面已经离开后还去改状态
onUnmounted(() => {
  clearTimeout(flashTimer)
})

// 有未保存改动时离开要拦一下 —— 选标签是手工活,静默丢失最招人烦
onBeforeRouteLeave(async () => {
  if (!isDirty.value) return true
  try {
    await ElMessageBox.confirm('兴趣标签有未保存的改动,离开将丢失。', '确认离开', {
      confirmButtonText: '放弃改动并离开',
      cancelButtonText: '留在本页',
      type: 'warning',
    })
    return true
  } catch {
    return false
  }
})
</script>

<template>
  <div v-loading="loading">
    <div class="page-head">
      <h1>兴趣标签</h1>
      <p>选择你的研究方向,权重越高代表越核心 —— 后续的检索与推荐都以它为依据</p>
    </div>

    <p v-if="loadError" class="alert" role="alert">{{ loadError }}</p>

    <div v-if="catalog" class="card picker">
      <div class="picker__head">
        <div>
          <h2>选择研究方向</h2>
          <p>点标签本体选中 / 取消(默认权重 3),点标签内第 N 格把权重设为 N,← → 微调</p>
        </div>
        <div class="picker__cap">
          <span class="picker__count" :class="{ 'is-over': limitFlash }" aria-live="polite">
            已选 <b>{{ selectedCount }}</b> / {{ maxTags }}
          </span>
          <span class="capbar" aria-hidden="true">
            <i :style="{ width: (selectedCount / maxTags) * 100 + '%' }" />
          </span>
        </div>
      </div>

      <div class="picker__body">
        <div>
          <section v-for="category in categories" :key="category.name" class="cat">
            <div class="cat__label">
              {{ category.name }}
              <em>{{ category.tags.length }}</em>
            </div>
            <div class="tags">
              <span
                v-for="tag in category.tags"
                :key="tag.key"
                class="tag"
                :class="{
                  'is-on': weightOf(tag.key) > 0,
                  'is-locked': isFull && weightOf(tag.key) === 0,
                }"
                :data-w="weightOf(tag.key)"
                role="button"
                tabindex="0"
                :aria-pressed="weightOf(tag.key) > 0"
                :aria-label="chipLabel(tag, weightOf(tag.key))"
                @click="onChipClick(tag.key, $event)"
                @keydown="onChipKeydown($event, tag.key)"
              >
                {{ tag.displayName }}
                <span
                  v-if="weightOf(tag.key) > 0"
                  class="ticks ticks--lead"
                  aria-hidden="true"
                >
                  <i
                    v-for="n in maxWeight"
                    :key="n"
                    class="seg"
                    :class="{ 'is-on': n <= weightOf(tag.key) }"
                  />
                </span>
              </span>
            </div>
          </section>
        </div>

        <aside class="side">
          <div class="side__head">
            <h3>已选强度</h3>
            <span class="card__meta">按权重降序</span>
          </div>
          <p class="side__hint" :class="{ 'is-over': limitFlash }">
            {{
              limitFlash
                ? `已达上限 ${maxTags} 个 —— 先取消一个,或降低某个标签的权重`
                : '每格 1 级,底色越深权重越高'
            }}
          </p>

          <ul v-if="ranked.length" class="rank">
            <li v-for="item in ranked" :key="item.tag">
              <span class="rank__name">{{ item.label }}</span>
              <WeightTicks :weight="item.weight" mini />
              <span class="rank__w">{{ item.weight }}</span>
            </li>
          </ul>
          <p v-else class="side__empty">
            还没有选中任何标签 —— 至少选 1 个,推荐才有依据。
          </p>

          <div class="side__legend">
            <WeightTicks :weight="3" mini />
            <span>= 权重 3 / 5,每格 1 级</span>
          </div>
        </aside>
      </div>

      <p v-if="saveError" class="alert picker__alert" role="alert">{{ saveError }}</p>

      <div class="picker__foot">
        <p class="picker__sum">
          已选 <b>{{ selectedCount }}</b> 个 · 核心方向(权重 ≥ 4)<b>{{ coreCount }}</b> 个
          <span v-if="isDirty" class="picker__dirty">· 有未保存的改动</span>
          <span v-else-if="savedAt" class="picker__saved">· 已保存 {{ savedAt }}</span>
        </p>
        <div class="picker__actions">
          <button class="linkbtn" type="button" :disabled="selectedCount === 0" @click="clearAll">
            清空
          </button>
          <el-button
            type="primary"
            size="small"
            :loading="saving"
            :disabled="!isDirty"
            @click="save"
          >
            {{ selectedCount === 0 ? '清空并保存' : `保存 ${selectedCount} 项` }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.picker__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--pp-space-6);
}

.picker__head h2 {
  margin: 0;
  font-size: var(--pp-text-lg);
  font-weight: var(--pp-weight-semibold);
  letter-spacing: var(--pp-tracking-tight);
}

.picker__head p {
  margin: 6px 0 0;
  font-size: var(--pp-text-sm);
  color: var(--pp-ink-3);
}

.picker__cap {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 7px;
}

.picker__count {
  font-family: var(--pp-font-mono);
  font-size: var(--pp-text-sm);
  color: var(--pp-ink-2);
  font-variant-numeric: tabular-nums;
}

.picker__count b {
  color: var(--pp-ink);
  font-weight: var(--pp-weight-semibold);
}

.picker__count.is-over,
.picker__count.is-over b {
  color: var(--pp-danger);
}

.capbar {
  width: 96px;
  height: 3px;
  border-radius: 2px;
  background: var(--pp-line);
  overflow: hidden;
}

.capbar i {
  display: block;
  height: 100%;
  background: var(--pp-accent);
  transition: width var(--pp-dur) var(--pp-ease);
}

.picker__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 264px;
  gap: var(--pp-space-6);
  margin-top: var(--pp-space-5);
}

.cat {
  padding-top: var(--pp-space-5);
  margin-top: var(--pp-space-5);
  border-top: 1px solid var(--pp-line);
}

.cat:first-of-type {
  border-top: 0;
  padding-top: 0;
  margin-top: 0;
}

.cat__label {
  display: flex;
  align-items: baseline;
  gap: var(--pp-space-2);
  margin-bottom: var(--pp-space-3);
  font-size: var(--pp-text-sm);
  font-weight: var(--pp-weight-medium);
  color: var(--pp-ink-2);
}

.cat__label em {
  font-style: normal;
  font-size: var(--pp-text-xs);
  color: var(--pp-ink-3);
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--pp-space-2);
}

.side {
  border-left: 1px solid var(--pp-line);
  padding-left: var(--pp-space-6);
}

.side__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--pp-space-2);
}

.side__head h3 {
  margin: 0;
  font-size: var(--pp-text-sm);
  font-weight: var(--pp-weight-semibold);
}

.side__hint {
  margin: 6px 0 var(--pp-space-3);
  font-size: var(--pp-text-xs);
  color: var(--pp-ink-3);
}

.side__hint.is-over {
  color: var(--pp-danger);
}

.rank {
  margin: 0;
  padding: 0;
  list-style: none;
}

.rank li {
  display: flex;
  align-items: center;
  gap: var(--pp-space-2);
  padding: 7px 0;
  border-top: 1px solid var(--pp-line);
  font-size: var(--pp-text-sm);
}

.rank li:first-child {
  border-top: 0;
}

.rank__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--pp-ink-2);
}

.rank__w {
  width: 1em;
  text-align: right;
  font-family: var(--pp-font-mono);
  font-size: var(--pp-text-xs);
  font-weight: var(--pp-weight-semibold);
  color: var(--pp-accent-deep);
  font-variant-numeric: tabular-nums;
}

.side__empty {
  margin: var(--pp-space-2) 0 0;
  font-size: var(--pp-text-xs);
  color: var(--pp-ink-3);
  line-height: 1.6;
}

.side__legend {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: var(--pp-space-4);
  padding-top: var(--pp-space-3);
  border-top: 1px solid var(--pp-line);
  font-size: var(--pp-text-xs);
  color: var(--pp-ink-3);
}

.picker__alert {
  margin: var(--pp-space-5) 0 0;
}

.picker__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pp-space-4);
  margin-top: var(--pp-space-6);
  padding-top: var(--pp-space-5);
  border-top: 1px solid var(--pp-line);
}

.picker__sum {
  margin: 0;
  font-size: var(--pp-text-sm);
  color: var(--pp-ink-3);
}

.picker__sum b {
  color: var(--pp-ink);
  font-weight: var(--pp-weight-semibold);
}

.picker__dirty {
  color: var(--pp-warning);
}

.picker__saved {
  color: var(--pp-success);
}

.picker__actions {
  display: flex;
  align-items: center;
  gap: var(--pp-space-4);
}

@media (max-width: 980px) {
  .picker__body {
    grid-template-columns: minmax(0, 1fr);
  }

  .side {
    border-left: 0;
    padding-left: 0;
    border-top: 1px solid var(--pp-line);
    padding-top: var(--pp-space-5);
  }
}
</style>
