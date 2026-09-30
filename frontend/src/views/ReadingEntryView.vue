<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { openByArxiv } from '../api/reading'

/**
 * 精读入口:粘贴 arXiv 编号或链接,直接开始读。
 *
 * 精读是这个项目的主功能,所以给它一个独立的入口 —— 用户往往就是**手里已经有一篇论文想读它**,
 * 不该要求他先去搜一遍、还得指望它出现在结果里。
 */

const router = useRouter()
const reference = ref('')
const opening = ref(false)
const error = ref('')

const EXAMPLES = ['2502.19271', 'https://arxiv.org/abs/2502.19271']

async function open() {
  const text = reference.value.trim()
  if (!text || opening.value) return

  opening.value = true
  error.value = ''
  try {
    const paper = await openByArxiv(text)
    // 拿到本地 id 就进精读页 —— 路由换掉,这一页不留在历史里
    await router.replace({ name: 'reading', params: { paperId: paper.id } })
  } catch (err) {
    // 401 已由请求层处理(登出 + 跳登录页)
    if (err.status !== 401) {
      error.value = err.message
    }
  } finally {
    opening.value = false
  }
}
</script>

<template>
  <div>
    <div class="page-head">
      <h1>精读</h1>
      <p>粘贴 arXiv 编号或链接,直接开始读 —— 不用先搜一遍</p>
    </div>

    <p v-if="error" class="alert" role="alert">{{ error }}</p>

    <form class="openbar" @submit.prevent="open">
      <el-input
        v-model="reference"
        size="large"
        :disabled="opening"
        placeholder="例如 2502.19271,或 https://arxiv.org/abs/2502.19271"
      />
      <el-button type="primary" size="large" native-type="submit" :loading="opening">
        开始精读
      </el-button>
    </form>

    <div class="examples">
      <span class="examples__label">试试:</span>
      <button
        v-for="example in EXAMPLES"
        :key="example"
        type="button"
        class="ex"
        @click="reference = example"
      >
        {{ example }}
      </button>
    </div>

    <section class="card notes">
      <h2 class="notes__title">现在能精读哪些论文</h2>
      <ul class="notes__list">
        <li>
          <b>能读</b>:arXiv 上有 HTML 版的论文 —— 实测近期约 90%、较早约 80%,
          连 2017 年的老论文也有
        </li>
        <li>
          <b>不能读</b>:期刊论文,以及你手上只有 PDF 文件的论文。那条路要 PDF 解析,还没做
        </li>
        <li>公式是<b>读 LaTeX 源码</b>而不是识别排版,所以带公式的部分也能对上</li>
      </ul>
      <p class="notes__hint">
        从检索结果里进来的话,带 arXiv 编号的那几篇会直接显示「精读」入口。
      </p>
    </section>
  </div>
</template>

<style scoped>
.openbar {
  display: flex;
  gap: var(--pp-space-3);
  margin-top: var(--pp-space-6);
}

.examples {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--pp-space-2);
  margin-top: var(--pp-space-4);
}

.examples__label {
  font-size: var(--pp-text-sm);
  color: var(--pp-ink-3);
}

.ex {
  padding: var(--pp-space-1) var(--pp-space-3);
  background: var(--pp-bg-surface);
  border: 1px solid var(--pp-line);
  border-radius: var(--pp-radius-pill);
  color: var(--pp-ink-2);
  font-family: var(--pp-font-mono);
  font-size: var(--pp-text-xs);
  cursor: pointer;
  transition: border-color var(--pp-dur) var(--pp-ease), color var(--pp-dur) var(--pp-ease);
}

.ex:hover {
  border-color: var(--pp-line-hover);
  color: var(--pp-ink);
}

.notes {
  margin-top: var(--pp-space-6);
}

.notes__title {
  margin: 0 0 var(--pp-space-3);
  font-size: var(--pp-text-md);
  font-weight: var(--pp-weight-semibold);
}

.notes__list {
  margin: 0;
  padding-left: var(--pp-space-5);
  font-size: var(--pp-text-sm);
  line-height: 1.9;
  color: var(--pp-ink-2);
}

.notes__hint {
  margin: var(--pp-space-4) 0 0;
  padding-top: var(--pp-space-3);
  border-top: 1px solid var(--pp-line);
  font-size: var(--pp-text-xs);
  color: var(--pp-ink-3);
}

@media (max-width: 980px) {
  .openbar {
    flex-direction: column;
  }
}
</style>
