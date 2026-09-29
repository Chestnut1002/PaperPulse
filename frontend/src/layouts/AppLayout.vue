<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import BrandMark from '../components/BrandMark.vue'
import { navItems } from '../router'
import { authStore } from '../stores/auth'

const router = useRouter()

const initial = computed(() => authStore.user?.username?.slice(0, 1) ?? '?')

function signOut() {
  authStore.signOut()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="shell">
    <header class="shell__top">
      <span class="brand">
        <BrandMark small />
        PaperPulse
      </span>

      <!-- 自绘导航而不用 el-menu:菜单组件的默认悬停底色与这套克制的观感冲突最重 -->
      <nav class="shell__nav">
        <router-link
          v-for="item in navItems"
          :key="item.name"
          :to="{ name: item.name }"
          class="shell__navlink"
        >
          {{ item.label }}
        </router-link>
      </nav>

      <div class="shell__user">
        <span class="avatar">{{ initial }}</span>
        <span class="shell__name">{{ authStore.user?.username }}</span>
        <span class="shell__sep" />
        <button class="linkbtn" type="button" @click="signOut">退出登录</button>
      </div>
    </header>

    <main class="shell__body">
      <div class="shell__inner">
        <router-view />
      </div>
    </main>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100%;
  background: var(--pp-bg-page);
}

.shell__top {
  display: flex;
  align-items: center;
  gap: var(--pp-space-8);
  height: var(--pp-topbar-h);
  padding: 0 var(--pp-space-6);
  background: var(--pp-bg-surface);
  border-bottom: 1px solid var(--pp-line);
}

.shell__nav {
  display: flex;
  align-items: center;
  gap: 26px;
  height: 100%;
}

.shell__navlink {
  position: relative;
  display: flex;
  align-items: center;
  height: 100%;
  color: var(--pp-ink-2);
  font-size: var(--pp-text-sm);
  text-decoration: none;
  transition: color var(--pp-dur) var(--pp-ease);
}

.shell__navlink:hover {
  color: var(--pp-ink);
}

/* 当前项:字重 + 2px 下划线,正好压在顶栏分隔线上 */
.shell__navlink.router-link-exact-active {
  color: var(--pp-ink);
  font-weight: var(--pp-weight-semibold);
}

.shell__navlink.router-link-exact-active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: var(--pp-accent);
  border-radius: 1px;
}

.shell__user {
  display: flex;
  align-items: center;
  gap: var(--pp-space-2);
  margin-left: auto;
}

.shell__name {
  font-size: var(--pp-text-sm);
  color: var(--pp-ink-2);
}

.shell__sep {
  width: 1px;
  height: 16px;
  background: var(--pp-line);
}

.shell__body {
  padding: var(--pp-space-8) var(--pp-space-10) var(--pp-space-12);
}

.shell__inner {
  max-width: var(--pp-content-max);
  margin: 0 auto;
}

@media (max-width: 980px) {
  .shell__top {
    gap: var(--pp-space-4);
    padding: 0 var(--pp-space-4);
  }

  .shell__nav {
    gap: var(--pp-space-4);
  }

  .shell__body {
    padding: var(--pp-space-6) var(--pp-space-4) var(--pp-space-8);
  }
}
</style>
