<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { addFavorite, fetchFavorites, recordRead, removeFavorite } from '../api/library'
import { searchPapers } from '../api/papers'
import { paperMeta } from '../utils/paper'

const route = useRoute()
const router = useRouter()

const query = ref('')
const searching = ref(false)
/** 是否已经检索过 —— 用来区分"还没搜"和"搜了但没结果",这两种空态该说的话不一样 */
const searched = ref(false)
const error = ref('')
const result = ref(null)

/** 已收藏的论文 id。Set 在 Vue 3 里是响应式的,可以直接增删。 */
const favoriteIds = ref(new Set())
/** 正在请求中的论文 id,避免连点两次发出两个请求 */
const pendingFavorite = ref(new Set())

const EXAMPLES = [
  '找 2024 年以后对比学习在推荐系统里的应用',
  '大模型做可解释推荐的最新工作',
  'graph neural network for citation recommendation',
]

async function runSearch(text) {
  const keyword = (text ?? query.value).trim()
  if (keyword.length < 2) {
    error.value = '请输入至少 2 个字的检索内容'
    return
  }

  query.value = keyword
  searching.value = true
  error.value = ''
  searched.value = true
  result.value = null
  try {
    result.value = await searchPapers(keyword)
    // 把查询同步到地址栏:刷新后还能看到同一批结果,链接也能直接分享
    router.replace({ query: { q: keyword } })
  } catch (err) {
    // 401 已由请求层处理(登出 + 跳登录页),这里只管把其它错误说出来
    if (err.status !== 401) {
      error.value = err.message
    }
  } finally {
    searching.value = false
  }
}

async function toggleFavorite(paper) {
  if (pendingFavorite.value.has(paper.id)) return
  pendingFavorite.value.add(paper.id)
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

/**
 * 点开论文链接 = 要读它,顺手记一次阅读。
 *
 * **不 await 也不打扰用户**:阅读历史是弱信号,记不上不值得打断"去看论文"这件事本身。
 * 但也不静默吞掉 —— 出了问题得在控制台留个痕。
 */
function openPaper(paper) {
  recordRead(paper.id).catch((err) => {
    if (err.status !== 401) {
      console.warn('记录阅读失败', err)
    }
  })
}

onMounted(async () => {
  try {
    const favorites = await fetchFavorites()
    favoriteIds.value = new Set(favorites.map((item) => item.paper.id))
  } catch {
    // 取不到收藏状态不该阻塞检索 —— 最坏结果只是按钮显示成"未收藏",点一下会幂等地补上
  }

  const initial = route.query.q
  if (initial) {
    query.value = initial
    await runSearch(initial)
  }
})
</script>

<template>
  <div>
    <div class="page-head">
      <h1>检索</h1>
      <p>用一句话描述你要找的论文,Agent 会把它拆成检索词,再去学术数据库里找</p>
    </div>

    <p v-if="error" class="alert" role="alert">{{ error }}</p>

    <form class="searchbar" @submit.prevent="runSearch()">
      <el-input
        v-model="query"
        size="large"
        :disabled="searching"
        placeholder="例如:找 2024 年以后对比学习在推荐系统里的应用"
      />
      <el-button type="primary" size="large" native-type="submit" :loading="searching">
        检索
      </el-button>
    </form>

    <!-- 还没搜过:给几个例子,比一句"请输入关键词"有用 -->
    <div v-if="!searched" class="hints">
      <p class="hints__title">试试这些:</p>
      <div class="hints__list">
        <button
          v-for="example in EXAMPLES"
          :key="example"
          type="button"
          class="hint"
          @click="runSearch(example)"
        >
          {{ example }}
        </button>
      </div>
    </div>

    <!-- 检索中:一次要十几秒,必须说清楚在等什么,不能只让按钮转圈 -->
    <div v-else-if="searching" class="waiting">
      <span class="waiting__dots" aria-hidden="true"><i /><i /><i /></span>
      正在拆解查询并检索文献,约需 10 秒…
    </div>

    <template v-else-if="result">
      <!-- Agent 的理解:让"为什么搜出来的是这些"可见 —— 结果不理想时,
           用户至少能分清是拆解错了,还是数据源里就没有 -->
      <section class="card plan">
        <div class="card__head">
          <h2>Agent 的理解</h2>
          <span class="card__meta">数据来自 {{ result.sourceLabel }}</span>
        </div>
        <dl class="plan__facts">
          <div class="plan__row">
            <dt>检索词</dt>
            <dd class="plan__keywords">{{ result.keywords }}</dd>
          </div>
          <div v-if="result.yearFrom" class="plan__row">
            <dt>时间范围</dt>
            <dd>{{ result.yearFrom }} 年至今</dd>
          </div>
        </dl>
        <p class="plan__rationale">{{ result.rationale }}</p>
      </section>

      <section class="card results">
        <div class="card__head">
          <h2>找到 {{ result.papers.length }} 篇</h2>
        </div>

        <p v-if="!result.papers.length" class="empty">
          没有找到相关论文 —— 换个说法,或去掉时间限制再试一次。
        </p>

        <ul v-else class="paper-list">
          <li v-for="paper in result.papers" :key="paper.id">
            <div class="paper-row__main">
              <a
                v-if="paper.url"
                class="paper-row__title"
                :href="paper.url"
                target="_blank"
                rel="noopener noreferrer"
                @click="openPaper(paper)"
              >
                {{ paper.title }}
              </a>
              <span v-else class="paper-row__title">{{ paper.title }}</span>

              <p class="paper-row__meta">{{ paperMeta(paper) }}</p>

              <p v-if="paper.abstractText" class="paper-row__abstract">
                {{ paper.abstractText }}
              </p>
            </div>

            <div class="paper-row__actions">
              <router-link
                class="link"
                :to="{ name: 'reading', params: { paperId: paper.id } }"
                :title="paper.arxivId
                  ? '用它的 arXiv 全文做问答'
                  : '这篇还没有 arXiv 编号:点进去会先拿标题去 arXiv 找预印本'"
              >
                {{ paper.arxivId ? '精读' : '找可读版本' }}
              </router-link>
              <el-button
                size="small"
                :type="favoriteIds.has(paper.id) ? 'default' : 'primary'"
                :loading="pendingFavorite.has(paper.id)"
                @click="toggleFavorite(paper)"
              >
                {{ favoriteIds.has(paper.id) ? '已收藏' : '收藏' }}
              </el-button>
            </div>
          </li>
        </ul>
      </section>
    </template>
  </div>
</template>

<style scoped>
.searchbar {
  display: flex;
  gap: var(--pp-space-3);
  margin-top: var(--pp-space-6);
}

.hints {
  margin-top: var(--pp-space-6);
}

.hints__title {
  margin: 0 0 var(--pp-space-3);
  font-size: var(--pp-text-sm);
  color: var(--pp-ink-3);
}

.hints__list {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--pp-space-2);
}

.hint {
  padding: var(--pp-space-2) var(--pp-space-3);
  background: var(--pp-bg-surface);
  border: 1px solid var(--pp-line);
  border-radius: var(--pp-radius-md);
  color: var(--pp-ink-2);
  font-family: inherit;
  font-size: var(--pp-text-sm);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--pp-dur) var(--pp-ease),
    color var(--pp-dur) var(--pp-ease);
}

.hint:hover {
  border-color: var(--pp-line-hover);
  color: var(--pp-ink);
}

.hint:focus-visible {
  outline: none;
  box-shadow: var(--pp-ring);
}

/* 「等待中」的样式在 styles/components.css —— 检索页与推荐页共用 */

.plan {
  margin-top: var(--pp-space-6);
}

.plan__facts {
  margin: var(--pp-space-4) 0 0;
}

.plan__row {
  display: flex;
  align-items: baseline;
  gap: var(--pp-space-4);
  padding: var(--pp-space-2) 0;
}

.plan__row dt {
  flex: none;
  width: 68px;
  font-size: var(--pp-text-sm);
  color: var(--pp-ink-3);
}

.plan__row dd {
  margin: 0;
  font-size: var(--pp-text-base);
  color: var(--pp-ink);
}

/* 检索词是给机器看的,用等宽体更容易一眼看清词与词的边界 */
.plan__keywords {
  font-family: var(--pp-font-mono);
  font-size: var(--pp-text-sm);
}

.plan__rationale {
  margin: var(--pp-space-3) 0 0;
  padding-top: var(--pp-space-3);
  border-top: 1px solid var(--pp-line);
  font-size: var(--pp-text-sm);
  color: var(--pp-ink-2);
  line-height: 1.6;
}

.results {
  margin-top: var(--pp-space-5);
}

.empty {
  margin: var(--pp-space-4) 0 0;
  font-size: var(--pp-text-sm);
  color: var(--pp-ink-3);
}

/* 论文行的样式在 styles/components.css —— 检索页与"我的论文"页共用同一套 */

@media (max-width: 980px) {
  .searchbar {
    flex-direction: column;
  }
}
</style>
