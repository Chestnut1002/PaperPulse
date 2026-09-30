<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { fetchMyInterests } from '../api/interests'
import { fetchFavorites, fetchHistory, fetchRatings } from '../api/library'
import { authStore } from '../stores/auth'
import { paperMeta } from '../utils/paper'

/**
 * 首页。
 *
 * <p><b>这一页刻意只用本地数据</b>(收藏 / 历史 / 评分 / 兴趣标签都是数据库里现成的),
 * 不碰任何外部接口 —— 所以它是**秒开**的。
 *
 * <p>推荐页要等十来秒(要按兴趣去外部拉候选),把它放在首页会让用户一登录就对着等待提示。
 * 等待应当发生在用户**主动选择**之后,而不是落地的那一刻。
 */

const router = useRouter()

const loading = ref(true)
const error = ref('')
const query = ref('')

const interests = ref([])
const favorites = ref([])
const history = ref([])
const ratings = ref([])

const coreCount = computed(() => interests.value.filter((item) => item.weight >= 4).length)
const recentFavorites = computed(() => favorites.value.slice(0, 3))

const stats = computed(() => [
  { key: 'favorites', label: '收藏', count: favorites.value.length },
  { key: 'history', label: '读过', count: history.value.length },
  { key: 'ratings', label: '评分', count: ratings.value.length },
])

async function load() {
  loading.value = true
  error.value = ''
  try {
    // 四个请求都只读本库,并发发出即可 —— 一次外部调用都没有
    const [myInterests, favoriteList, historyList, ratingList] = await Promise.all([
      fetchMyInterests(),
      fetchFavorites(),
      fetchHistory(),
      fetchRatings(),
    ])
    interests.value = myInterests
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

/** 首页只负责把查询带走;真正的检索在检索页发生,等待也就发生在那里。 */
function submitSearch() {
  const keyword = query.value.trim()
  if (keyword.length < 2) return
  router.push({ name: 'search', query: { q: keyword } })
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="page-head">
      <h1>欢迎回来,{{ authStore.user?.username }}</h1>
      <p>想找新论文就直接搜,或者去看看为你挑的推荐</p>
    </div>

    <p v-if="error" class="alert" role="alert">{{ error }}</p>

    <form class="searchbar" @submit.prevent="submitSearch">
      <el-input
        v-model="query"
        size="large"
        placeholder="用一句话描述你要找的论文,例如:2024 年以后对比学习在推荐系统里的应用"
      />
      <el-button type="primary" size="large" native-type="submit">检索</el-button>
    </form>

    <div class="stats">
      <router-link v-for="item in stats" :key="item.key" class="stat card" :to="{ name: 'library' }">
        <span class="stat__count">{{ item.count }}</span>
        <span class="stat__label">{{ item.label }}</span>
      </router-link>
    </div>

    <div class="grid">
      <section class="card">
        <div class="card__head">
          <h2>最近收藏</h2>
          <router-link class="link" :to="{ name: 'library' }">全部</router-link>
        </div>

        <ul v-if="recentFavorites.length" class="paper-list">
          <li v-for="item in recentFavorites" :key="item.paper.id">
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
              <p class="paper-row__meta">{{ paperMeta(item.paper) }}</p>
            </div>
          </li>
        </ul>
        <p v-else class="empty">
          还没有收藏 —— 去「检索」里找几篇,或者在「今日推荐」里看看。
        </p>
      </section>

      <aside class="card side">
        <div class="card__head">
          <h2>兴趣标签</h2>
          <router-link class="link" :to="{ name: 'interests' }">管理</router-link>
        </div>

        <div v-if="interests.length" class="side__tags">
          <span
            v-for="item in interests"
            :key="item.tag"
            class="tag tag--mini tag--static is-on"
            :data-w="item.weight"
            :aria-label="`${item.displayName},权重 ${item.weight} / 5`"
          >
            {{ item.displayName }}
          </span>
        </div>
        <p v-else class="side__empty">
          还没有选中任何标签 —— 至少选 1 个,推荐才有依据。
        </p>

        <p class="card__meta side__note">
          {{ interests.length }} / 10 已选 · 核心方向(权重 ≥ 4){{ coreCount }} 个
        </p>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.searchbar {
  display: flex;
  gap: var(--pp-space-3);
  margin-top: var(--pp-space-6);
}

/* 三张统计卡:数字用等宽体,大小一致才好横向比较 */
.stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--pp-space-4);
  margin-top: var(--pp-space-5);
}

.stat {
  display: flex;
  align-items: baseline;
  gap: var(--pp-space-2);
  padding: var(--pp-space-4) var(--pp-space-5);
  text-decoration: none;
  transition: border-color var(--pp-dur) var(--pp-ease);
}

.stat:hover {
  border-color: var(--pp-line-strong);
}

.stat__count {
  font-family: var(--pp-font-mono);
  font-size: var(--pp-text-xl);
  font-weight: var(--pp-weight-semibold);
  color: var(--pp-ink);
  font-variant-numeric: tabular-nums;
}

.stat__label {
  font-size: var(--pp-text-sm);
  color: var(--pp-ink-3);
}

.grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: var(--pp-space-5);
  margin-top: var(--pp-space-5);
}

.empty {
  margin: var(--pp-space-4) 0 0;
  font-size: var(--pp-text-sm);
  color: var(--pp-ink-3);
}

.side__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--pp-space-2);
  margin-top: var(--pp-space-4);
}

.side__empty {
  margin: var(--pp-space-4) 0 0;
  font-size: var(--pp-text-xs);
  color: var(--pp-ink-3);
}

.side__note {
  margin: var(--pp-space-4) 0 0;
  padding-top: var(--pp-space-3);
  border-top: 1px solid var(--pp-line);
}

@media (max-width: 980px) {
  .searchbar {
    flex-direction: column;
  }

  .grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
