<script setup>
// 全站唯一的图形标识:一段脉冲折线。尺寸只有常规与紧凑两档。
defineProps({
  small: { type: Boolean, default: false },
})
</script>

<template>
  <span class="mark" :class="{ 'mark--sm': small }">
    <svg
      :width="small ? 13 : 14"
      :height="small ? 13 : 14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      <path d="M2 12h3.6l2.4-6.4 3.6 12.8 2.4-7.2 1.4 1.6h6.6" />
    </svg>
  </span>
</template>
