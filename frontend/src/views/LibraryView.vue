<script setup>
import { computed, onMounted, ref } from 'vue'

import {
  clearHistory,
  fetchFavorites,
  fetchHistory,
  fetchRatings,
  ratePaper,
  removeFavorite,
  removeRating,
} from '../api/library'
import { formatTime, paperMeta } from '../utils/paper'

const TABS = [
  { key: 'favorites', label: '收藏' },
  { key: 'history', label: '阅读历史' },
  { key: 'ratings', label: '评分' },
]

const EMPTY_TEXT = {
  favorites: '还没有收藏任何论文 —— 去「检索」里找几篇,点收藏就会出现在这里。',
  history: '还没有阅读记录 —— 在检索结果里点开论文链接,会自动记一笔。',
  ratings: '还没有给论文打过分 —— 在「收藏」里给论文评分,就会汇总到这里。',
}

const activeTab = ref('favorites')
const loading = ref(true)
const error = ref('')

const favorites = ref([])
const history = ref([])
const ratings = ref([])

/** 正在请求中的 paperId,防止连点发出重复请求 */
const busy = ref(new Set())

const counts = computed(() => ({
  favorites: favorites.value.length,
  history: history.value.length,
  ratings: ratings.value.length,
}))

/** paperId -> 分数,给评分控件读当前值用。 */
const scoreByPaper = computed(() => {
  const map = new Map()
  for (const item of ratings.value) {
    map.set(item.paper.id, item.score)
  }
  return map
})

function scoreOf(paperId) {
  return scoreByPaper.value.get(paperId) ?? 0
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [favoriteList, historyList, ratingList] = await Promise.all([
      fetchFavorites(),
      fetchHistory(),
      fetchRatings(),
    ])
    favorites.value = favoriteList
    history.value = historyList
    ratings.value = ratingList
  } catch (err) {
    if (err.status !== 401) {
      error.value = err.message
    }
  } finally {
    loading.value = false
  }
}

/** 包一层"正在忙":防连点,并保证出错时用户看得见。 */
async function run(paperId, action) {
  if (busy.value.has(paperId)) return
  busy.value.add(paperId)
  error.value = ''
  try {
    await action()
  } catch (err) {
    if (err.status !== 401) {
      error.value = err.message
    }
  } finally {
    busy.value.delete(paperId)
  }
}

const cancelFavorite = (paper) =>
  run(paper.id, async () => {
    await removeFavorite(paper.id)
    favorites.value = favorites.value.filter((item) => item.paper.id !== paper.id)
  })

const setScore = (paper, score) =>
  run(paper.id, async () => {
    // el-rate 清空时会给 0,那不是"打 0 分"的意思 —— 取消评分是另一个动作
    if (!score) return

    const updated = await ratePaper(paper.id, score)
    const existing = ratings.value.find((item) => item.paper.id === paper.id)
    if (existing) {
      existing.score = updated.score
      existing.ratedAt = updated.ratedAt
    } else {
      ratings.value = [updated, ...ratings.value]
    }
  })

const cancelRating = (paper) =>
  run(paper.id, async () => {
    await removeRating(paper.id)
    ratings.value = ratings.value.filter((item) => item.paper.id !== paper.id)
  })

async function clearAllHistory() {
  try {
    await ElMessageBox.confirm('清空后无法恢复。', '清空阅读历史', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return // 用户取消
  }

  error.value = ''
  try {
    await clearHistory()
    history.value = []
  } catch (err) {
    if (err.status !== 401) {
      error.value = err.message
    }
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="page-head">
      <h1>我的论文</h1>
      <p>收藏、读过的、打过分的,都汇总在这里</p>
    </div>

    <p v-if="error" class="alert" role="alert">{{ error }}</p>

    <div class="tabs" role="tablist">
      <button
        v-for="tab in TABS"
        :key="tab.key"
        type="button"
        role="tab"
        class="tabs__item"
        :class="{ 'is-active': activeTab === tab.key }"
        :aria-selected="activeTab === tab.key"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
        <span class="tabs__count">{{ counts[tab.key] }}</span>
      </button>
    </div>

    <!-- 收藏:在这里顺手评分,rating 才有入口 -->
    <section v-if="activeTab === 'favorites'" class="card">
      <ul v-if="favorites.length" class="paper-list">
        <li v-for="item in favorites" :key="item.paper.id">
          <div class="paper-row__main">
            <a
              v-if="item.paper.url"
              class="paper-row__title"
              :href="item.paper.url"
              target="_blank"
              rel="noopener noreferrer"
            >
              {{ item.paper.title }}
            </a>
            <span v-else class="paper-row__title">{{ item.paper.title }}</span>
            <p class="paper-row__meta">
              {{ paperMeta(item.paper) }} · 收藏于 {{ formatTime(item.favoritedAt) }}
            </p>
          </div>
          <div class="paper-row__actions">
            <router-link
              v-if="item.paper.arxivId"
              class="link"
              :to="{ name: 'reading', params: { paperId: item.paper.id } }"
            >
              精读
            </router-link>
            <el-rate
              :model-value="scoreOf(item.paper.id)"
              :disabled="busy.has(item.paper.id)"
              @change="(value) => setScore(item.paper, value)"
            />
            <el-button
              size="small"
              :loading="busy.has(item.paper.id)"
              @click="cancelFavorite(item.paper)"
            >
              取消收藏
            </el-button>
          </div>
        </li>
      </ul>
      <p v-else class="empty">{{ EMPTY_TEXT.favorites }}</p>
    </section>

    <!-- 阅读历史 -->
    <section v-else-if="activeTab === 'history'" class="card">
      <div class="card__head">
        <h2>读过的论文</h2>
        <button
          v-if="history.length"
          type="button"
          class="linkbtn"
          @click="clearAllHistory"
        >
          清空历史
        </button>
      </div>

      <ul v-if="history.length" class="paper-list">
        <li v-for="item in history" :key="item.paper.id">
          <div class="paper-row__main">
            <a
              v-if="item.paper.url"
              class="paper-row__title"
              :href="item.paper.url"
              target="_blank"
              rel="noopener noreferrer"
            >
              {{ item.paper.title }}
            </a>
            <span v-else class="paper-row__title">{{ item.paper.title }}</span>
            <p class="paper-row__meta">
              {{ paperMeta(item.paper) }} · 最近 {{ formatTime(item.lastReadAt) }} · 读过
              {{ item.readCount }} 次
            </p>
          </div>
        </li>
      </ul>
      <p v-else class="empty">{{ EMPTY_TEXT.history }}</p>
    </section>

    <!-- 评分 -->
    <section v-else class="card">
      <ul v-if="ratings.length" class="paper-list">
        <li v-for="item in ratings" :key="item.paper.id">
          <div class="paper-row__main">
            <a
              v-if="item.paper.url"
              class="paper-row__title"
              :href="item.paper.url"
              target="_blank"
              rel="noopener noreferrer"
            >
              {{ item.paper.title }}
            </a>
            <span v-else class="paper-row__title">{{ item.paper.title }}</span>
            <p class="paper-row__meta">
              {{ paperMeta(item.paper) }} · 评于 {{ formatTime(item.ratedAt) }}
            </p>
          </div>
          <div class="paper-row__actions">
            <el-rate
              :model-value="item.score"
              :disabled="busy.has(item.paper.id)"
              @change="(value) => setScore(item.paper, value)"
            />
            <el-button
              size="small"
              :loading="busy.has(item.paper.id)"
              @click="cancelRating(item.paper)"
            >
              取消评分
            </el-button>
          </div>
        </li>
      </ul>
      <p v-else class="empty">{{ EMPTY_TEXT.ratings }}</p>
    </section>
  </div>
</template>

<style scoped>
/* 页内标签:沿用顶栏导航的语言(当前项加粗 + 2px 蓝下划线),不再引入一套新的观感 */
.tabs {
  display: flex;
  align-items: center;
  gap: var(--pp-space-6);
  margin-top: var(--pp-space-6);
  border-bottom: 1px solid var(--pp-line);
}

.tabs__item {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 0 10px;
  border: 0;
  background: none;
  color: var(--pp-ink-2);
  font-family: inherit;
  font-size: var(--pp-text-base);
  cursor: pointer;
  transition: color var(--pp-dur) var(--pp-ease);
}

.tabs__item:hover {
  color: var(--pp-ink);
}

.tabs__item.is-active {
  color: var(--pp-ink);
  font-weight: var(--pp-weight-semibold);
}

.tabs__item.is-active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: var(--pp-accent);
  border-radius: 1px;
}

.tabs__item:focus-visible {
  outline: none;
  box-shadow: var(--pp-ring);
  border-radius: var(--pp-radius-sm);
}

.tabs__count {
  font-family: var(--pp-font-mono);
  font-size: var(--pp-text-xs);
  color: var(--pp-ink-3);
  font-variant-numeric: tabular-nums;
}

.card {
  margin-top: var(--pp-space-5);
}

.empty {
  margin: 0;
  font-size: var(--pp-text-sm);
  color: var(--pp-ink-3);
}

@media (max-width: 980px) {
  .paper-list li {
    flex-direction: column;
  }
}
</style>
