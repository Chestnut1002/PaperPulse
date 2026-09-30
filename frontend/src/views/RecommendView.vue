<script setup>
import { computed, onMounted, ref } from 'vue'

import { fetchMyInterests } from '../api/interests'
import { addFavorite, fetchFavorites, recordRead, removeFavorite } from '../api/library'
import { fetchRecommendations } from '../api/recommendations'
import { paperMeta } from '../utils/paper'

const loading = ref(true)
const error = ref('')
const result = ref(null)
const interests = ref([])

const favoriteIds = ref(new Set())
const pendingFavorite = ref(new Set())

const coreCount = computed(() => interests.value.filter((item) => item.weight >= 4).length)

const recommendations = computed(() => result.value?.recommendations ?? [])
/** 后端在没有兴趣标签时会把引导文案放在 hint 里,而不是报错 */
const hint = computed(() => result.value?.hint ?? '')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [found, myInterests, favorites] = await Promise.all([
      fetchRecommendations(),
      fetchMyInterests(),
      fetchFavorites(),
    ])
    result.value = found
    interests.value = myInterests
    favoriteIds.value = new Set(favorites.map((item) => item.paper.id))
  } catch (err) {
    // 401 已由请求层处理(登出 + 跳登录页)
    if (err.status !== 401) {
      error.value = err.message
    }
  } finally {
    loading.value = false
  }
}

async function toggleFavorite(paper) {
  if (pendingFavorite.value.has(paper.id)) return
  pendingFavorite.value.add(paper.id)
  error.value = ''
  try {
    if (favoriteIds.value.has(paper.id)) {
      await removeFavorite(paper.id)
      favoriteIds.value.delete(paper.id)
    } else {
      await addFavorite(paper.id)
      favoriteIds.value.add(paper.id)
    }
  } catch (err) {
    if (err.status !== 401) {
      error.value = err.message
    }
  } finally {
    pendingFavorite.value.delete(paper.id)
  }
}

/** 点开链接 = 要读它,顺手记一次阅读。失败不打扰用户 —— 弱信号不该挡住看论文。 */
function openPaper(paper) {
  recordRead(paper.id).catch((err) => {
    if (err.status !== 401) {
      console.warn('记录阅读失败', err)
    }
  })
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="page-head">
      <h1>今日推荐</h1>
      <p v-if="interests.length">
        基于你的 {{ interests.length }} 个兴趣标签(核心方向 {{ coreCount }} 个)
        <template v-if="result?.sourceLabel"> · 候选来自 {{ result.sourceLabel }}</template>
      </p>
      <p v-else>按你的兴趣标签找的论文,每条都说明为什么推荐它</p>
    </div>

    <p v-if="error" class="alert" role="alert">{{ error }}</p>

    <div v-if="loading" class="waiting">
      <span class="waiting__dots" aria-hidden="true"><i /><i /><i /></span>
      正在按你的兴趣找论文,约需 10 秒…
    </div>

    <template v-else-if="hint">
      <section class="card guide">
        <p class="guide__text">{{ hint }}</p>
        <router-link class="link" :to="{ name: 'interests' }">去选兴趣标签 →</router-link>
      </section>
    </template>

    <div v-else class="grid">
      <section class="card">
        <div class="card__head">
          <h2>为你找到 {{ recommendations.length }} 篇</h2>
          <span class="card__meta">按兴趣匹配度排序</span>
        </div>

        <p v-if="!recommendations.length" class="empty">
          这次没找到合适的候选 —— 稍后再试,或者去「兴趣标签」里调整一下方向。
        </p>

        <ul v-else class="paper-list">
          <li v-for="item in recommendations" :key="item.paper.id">
            <div class="paper-row__main">
              <a
                v-if="item.paper.url"
                class="paper-row__title"
                :href="item.paper.url"
                target="_blank"
                rel="noopener noreferrer"
                @click="openPaper(item.paper)"
              >
                {{ item.paper.title }}
              </a>
              <span v-else class="paper-row__title">{{ item.paper.title }}</span>

              <p class="paper-row__meta">{{ paperMeta(item.paper) }}</p>
              <!-- 推荐理由:让"为什么给我看这个"可见 -->
              <p class="reason">{{ item.reason }}</p>
            </div>

            <div class="paper-row__actions">
              <router-link
                class="link"
                :to="{ name: 'reading', params: { paperId: item.paper.id } }"
                :title="item.paper.arxivId
                  ? '用它的 arXiv 全文做问答'
                  : '这篇还没有 arXiv 编号:点进去会先拿标题去 arXiv 找预印本'"
              >
                {{ item.paper.arxivId ? '精读' : '找可读版本' }}
              </router-link>
              <el-button
                size="small"
                :type="favoriteIds.has(item.paper.id) ? 'default' : 'primary'"
                :loading="pendingFavorite.has(item.paper.id)"
                @click="toggleFavorite(item.paper)"
              >
                {{ favoriteIds.has(item.paper.id) ? '已收藏' : '收藏' }}
              </el-button>
            </div>
          </li>
        </ul>
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
        <p v-else class="side__empty">还没有选中任何标签。</p>

        <p class="card__meta side__note">
          推荐以这些标签为依据;权重越高的方向,推得越多
        </p>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.guide {
  margin-top: var(--pp-space-6);
  text-align: center;
}

.guide__text {
  margin: 0 0 var(--pp-space-3);
  font-size: var(--pp-text-base);
  color: var(--pp-ink-2);
}

.grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: var(--pp-space-5);
  margin-top: var(--pp-space-6);
}

.empty {
  margin: var(--pp-space-4) 0 0;
  font-size: var(--pp-text-sm);
  color: var(--pp-ink-3);
}

/* 推荐理由:整行列表里最该被看见的一行,靠颜色而不是装饰来区分 */
.reason {
  margin: 8px 0 0;
  font-size: var(--pp-text-xs);
  color: var(--pp-accent-deep);
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
  .grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
