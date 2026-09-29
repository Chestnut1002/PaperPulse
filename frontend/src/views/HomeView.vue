<script setup>
import { computed, onMounted, ref } from 'vue'

import { fetchCurrentUser } from '../api/auth'
import { fetchMyInterests } from '../api/interests'
import WeightTicks from '../components/WeightTicks.vue'
import { authStore } from '../stores/auth'

const loading = ref(true)
const loadError = ref('')
const interests = ref([])

const createdAt = computed(() => {
  const raw = authStore.user?.createdAt
  return raw ? new Date(raw).toLocaleString() : '—'
})

const coreCount = computed(() => interests.value.filter((item) => item.weight >= 4).length)

onMounted(async () => {
  try {
    // 回查用户:token 签名有效不等于用户仍然存在(可能已被删除)。
    // 这一步顺带验证了手里的 token 依旧可用 —— 失效会走 401,由请求层登出并跳登录页。
    const [user, myInterests] = await Promise.all([fetchCurrentUser(), fetchMyInterests()])
    authStore.setUser(user)
    interests.value = myInterests
  } catch (error) {
    // 401 已在请求层处理完毕;其它错误提示一下即可,不阻塞页面
    if (error.status !== 401) {
      loadError.value = error.message
    }
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading">
    <div class="page-head">
      <h1>首页</h1>
      <p>当前账号信息来自服务端实查结果</p>
    </div>

    <p v-if="loadError" class="alert" role="alert">{{ loadError }}</p>

    <div class="grid">
      <section class="card">
        <div class="card__head">
          <h2>账号信息</h2>
        </div>
        <dl class="facts">
          <div class="facts__row">
            <dt>用户名</dt>
            <dd>{{ authStore.user?.username ?? '—' }}</dd>
          </div>
          <div class="facts__row">
            <dt>邮箱</dt>
            <dd>{{ authStore.user?.email ?? '—' }}</dd>
          </div>
          <div class="facts__row">
            <dt>注册时间</dt>
            <dd>{{ createdAt }}</dd>
          </div>
        </dl>
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
            <WeightTicks :weight="item.weight" mini lead />
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
.grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: var(--pp-space-5);
  margin-top: var(--pp-space-6);
}

/* 只靠 1px 分隔线做行,不用斑马纹、不嵌套卡片 */
.facts {
  margin: var(--pp-space-3) 0 0;
}

.facts__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--pp-space-4);
  padding: var(--pp-space-4) 0;
  border-top: 1px solid var(--pp-line);
}

.facts__row:first-child {
  border-top: 0;
  padding-top: var(--pp-space-3);
}

.facts__row:last-child {
  padding-bottom: 0;
}

.facts dt {
  font-size: var(--pp-text-sm);
  color: var(--pp-ink-3);
}

.facts dd {
  margin: 0;
  font-size: var(--pp-text-md);
  color: var(--pp-ink);
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
  line-height: 1.6;
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
