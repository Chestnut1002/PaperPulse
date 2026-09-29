<script setup>
import { computed } from 'vue'

/**
 * 权重刻度:5 格,点亮格数 = 权重。
 *
 * 这是权重的第二重编码 —— 只靠底色深浅的话,色觉障碍下读不出强度。
 * 纯装饰,语义由外层元素的 aria-label 承担,所以这里 aria-hidden。
 */
const props = defineProps({
  weight: { type: Number, required: true },
  /** 紧凑档:用于右栏名单与首页小标签 */
  mini: { type: Boolean, default: false },
  /** chip 内使用时需要与文字拉开一点距离 */
  lead: { type: Boolean, default: false },
})

const cells = computed(() => Array.from({ length: 5 }, (_, index) => index < props.weight))
</script>

<template>
  <span
    class="ticks"
    :class="{ 'ticks--mini': mini, 'ticks--lead': lead }"
    aria-hidden="true"
  >
    <i v-for="(lit, index) in cells" :key="index" class="seg" :class="{ 'is-on': lit }" />
  </span>
</template>
