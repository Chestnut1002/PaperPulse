<script setup>
import { computed, onMounted, ref } from 'vue'

import { fetchCurrentUser } from '../api/auth'
import { authStore } from '../stores/auth'

const loading = ref(true)

// 后端返回的是 ISO 8601 字符串,直接展示是一串带纳秒的时间戳,按本地时区格式化
const createdAt = computed(() => {
  const raw = authStore.user?.createdAt
  return raw ? new Date(raw).toLocaleString() : ''
})

onMounted(async () => {
  try {
    // 回查一次用户:token 签名有效不等于用户仍然存在(可能已被删除)。
    // 这一步顺带验证了手里的 token 依旧可用 —— 失效会走 401,由请求层登出并跳登录页。
    authStore.setUser(await fetchCurrentUser())
  } catch (error) {
    // 401 已在请求层处理完毕;其它错误提示一下即可,不阻塞页面
    if (error.status !== 401) {
      ElMessage.error(error.message)
    }
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading" class="home">
    <el-card>
      <h2 class="home__title">欢迎回来,{{ authStore.user?.username }}</h2>
      <p class="home__hint">当前账号信息来自服务端实查结果。</p>

      <el-descriptions :column="1" border>
        <el-descriptions-item label="用户名">{{ authStore.user?.username }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ authStore.user?.email }}</el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ createdAt }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card class="home__next">
      <h3 class="home__next-title">接下来</h3>
      <ul class="home__next-list">
        <li>兴趣标签选择器 —— 34 个标签、1~5 级兴趣强度</li>
        <li>收藏 / 阅读历史 / 评分管理</li>
        <li>检索 Agent(REQ-002)</li>
      </ul>
    </el-card>
  </div>
</template>

<style scoped>
.home {
  max-width: 720px;
  margin: 0 auto;
}

.home__title {
  margin: 0 0 8px;
  font-size: 20px;
}

.home__hint {
  margin: 0 0 20px;
  color: #909399;
  font-size: 14px;
}

.home__next {
  margin-top: 16px;
}

.home__next-title {
  margin: 0 0 12px;
  font-size: 16px;
}

.home__next-list {
  margin: 0;
  padding-left: 20px;
  color: #606266;
  line-height: 1.9;
}
</style>
