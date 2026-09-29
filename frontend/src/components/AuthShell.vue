<script setup>
import BrandMark from './BrandMark.vue'

// 登录与注册共用的外壳:居中卡片 + 品牌行 + 顶部一层很淡的蓝。
// 内容由各页面通过插槽提供,标题 / 说明 / 底部链接的样式在 styles/components.css 里共用。
</script>

<template>
  <div class="auth-page">
    <div class="auth">
      <span class="brand">
        <BrandMark />
        PaperPulse
      </span>
      <slot />
    </div>
  </div>
</template>

<style scoped>
/* 登录页唯一的背景装饰:顶部一层 6% 的蓝,55% 处渐隐到白 */
.auth-page {
  display: grid;
  place-items: center;
  min-height: 100%;
  padding: var(--pp-space-6);
  background:
    radial-gradient(60% 55% at 50% 0%, rgba(37, 99, 235, 0.06), rgba(37, 99, 235, 0) 70%),
    var(--pp-bg-surface);
}

.auth {
  width: 100%;
  max-width: 356px;
  padding: var(--pp-space-10) var(--pp-space-10) var(--pp-space-8);
}
</style>
