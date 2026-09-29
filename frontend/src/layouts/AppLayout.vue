<script setup>
import { useRouter } from 'vue-router'

import { authStore } from '../stores/auth'

const router = useRouter()

function signOut() {
  authStore.signOut()
  router.push({ name: 'login' })
}
</script>

<template>
  <el-container class="layout">
    <el-header class="layout__header">
      <div class="layout__brand">PaperPulse</div>
      <div class="layout__user">
        <span class="layout__username">{{ authStore.user?.username }}</span>
        <el-button link type="primary" @click="signOut">退出登录</el-button>
      </div>
    </el-header>

    <el-main class="layout__main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100%;
}

.layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.layout__brand {
  font-size: 18px;
  font-weight: 600;
}

.layout__user {
  display: flex;
  align-items: center;
  gap: 12px;
}

.layout__username {
  color: #606266;
  font-size: 14px;
}

.layout__main {
  padding: 24px;
}
</style>
