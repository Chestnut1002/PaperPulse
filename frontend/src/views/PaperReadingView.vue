<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { askQuestion, fetchPaper } from '../api/reading'
import { paperMeta } from '../utils/paper'

const route = useRoute()
const paperId = route.params.paperId

const loadingPaper = ref(true)
const paper = ref(null)
const loadError = ref('')

const question = ref('')
const asking = ref(false)
const askError = ref('')

/** 对话历史。**由前端持有** —— 服务端不维持会话状态,重启不丢对话。 */
const messages = ref([])

/** 发给服务端的历史轮数。更早的由服务端按上下文预算再截断。 */
const HISTORY_TURNS = 8

const readable = computed(() => Boolean(paper.value?.arxivId))

/** 只保留最近若干轮,并转成服务端要的形状。 */
function historyPayload() {
  return messages.value
    .slice(-HISTORY_TURNS)
    .map((message) => ({ role: message.role, content: message.content }))
}

/**
 * 把回答切成普通文本与 [[§n]] 标记两种片段。
 *
 * **不用 v-html**:回答是模型生成的,直接当 HTML 渲染等于把 XSS 的口子交给它。
 * 切成片段后用普通文本插值渲染,标记另加样式。
 */
function segmentsOf(text) {
  return text
    .split(/(\[\[§\d+\]\])/g)
    .filter((part) => part !== '')
    .map((part) => {
      const match = part.match(/^\[\[§(\d+)\]\]$/)
      return match ? { cite: Number(match[1]) } : { text: part }
    })
}

async function send() {
  const text = question.value.trim()
  if (text.length < 2 || asking.value || !readable.value) return

  const history = historyPayload()
  messages.value.push({ role: 'user', content: text })
  question.value = ''
  asking.value = true
  askError.value = ''

  try {
    const result = await askQuestion(paperId, text, history)
    messages.value.push({
      role: 'assistant',
      content: result.answer,
      citations: result.citations ?? [],
      omittedTurns: result.omittedTurns ?? 0,
    })
  } catch (error) {
    // 401 已由请求层处理(登出 + 跳登录页)
    if (error.status !== 401) {
      askError.value = error.message
      // 提问失败就把那一轮撤回来,免得历史里留下一句没有回答的话
      messages.value.pop()
      question.value = text
    }
  } finally {
    asking.value = false
  }
}

onMounted(async () => {
  try {
    paper.value = await fetchPaper(paperId)
  } catch (error) {
    if (error.status !== 401) {
      loadError.value = error.message
    }
  } finally {
    loadingPaper.value = false
  }
})
</script>

<template>
  <div v-loading="loadingPaper">
    <div class="page-head">
      <h1>{{ paper?.title ?? '论文精读' }}</h1>
      <p v-if="paper">{{ paperMeta(paper) }}</p>
    </div>

    <p v-if="loadError" class="alert" role="alert">{{ loadError }}</p>

    <template v-if="paper">
      <!-- 读不了就说清楚为什么,并且不给出输入框 —— 免得用户白问一场 -->
      <section v-if="!readable" class="card unavailable">
        <p class="unavailable__text">
          这篇论文没有可精读的全文 —— 目前只支持能从 arXiv 取到全文的论文。
        </p>
        <a v-if="paper.url" class="link" :href="paper.url" target="_blank" rel="noopener noreferrer">
          打开原文链接 →
        </a>
      </section>

      <section v-else class="card">
        <div v-if="!messages.length" class="hints">
          <p class="hints__title">可以这样问:</p>
          <div class="hints__list">
            <button
              v-for="example in ['这篇论文解决了什么问题?', '它的方法具体怎么做的?', '实验结果如何?']"
              :key="example"
              type="button"
              class="hint"
              @click="question = example"
            >
              {{ example }}
            </button>
          </div>
        </div>

        <ul v-else class="thread">
          <li v-for="(message, index) in messages" :key="index" :class="`thread__item thread__item--${message.role}`">
            <p v-if="message.role === 'user'" class="thread__question">{{ message.content }}</p>

            <template v-else>
              <p v-if="message.omittedTurns" class="thread__note">
                已省略更早的 {{ message.omittedTurns }} 轮对话(超出上下文长度)
              </p>
              <p class="thread__answer">
                <template v-for="(segment, part) in segmentsOf(message.content)" :key="part">
                  <sup v-if="segment.cite" class="cite">§{{ segment.cite }}</sup>
                  <template v-else>{{ segment.text }}</template>
                </template>
              </p>

              <div v-if="message.citations?.length" class="sources">
                <p class="sources__title">依据</p>
                <details v-for="citation in message.citations" :key="citation.index" class="source">
                  <summary class="source__summary">
                    <span class="cite">§{{ citation.index }}</span>
                    {{ citation.title }}
                  </summary>
                  <p class="source__excerpt">{{ citation.excerpt }}</p>
                </details>
              </div>
            </template>
          </li>
        </ul>

        <p v-if="asking" class="waiting">
          <span class="waiting__dots" aria-hidden="true"><i /><i /><i /></span>
          正在读这篇论文,约需十秒…
        </p>

        <p v-if="askError" class="alert thread__error" role="alert">{{ askError }}</p>

        <form class="askbar" @submit.prevent="send">
          <el-input
            v-model="question"
            size="large"
            :disabled="asking"
            placeholder="就这篇论文提问,例如:它的方法解决了什么问题?"
          />
          <el-button type="primary" size="large" native-type="submit" :loading="asking">
            提问
          </el-button>
        </form>
      </section>
    </template>
  </div>
</template>

<style scoped>
.card {
  margin-top: var(--pp-space-5);
}

.unavailable {
  text-align: center;
}

.unavailable__text {
  margin: 0 0 var(--pp-space-3);
  font-size: var(--pp-text-base);
  color: var(--pp-ink-2);
}

.hints {
  margin-bottom: var(--pp-space-5);
}

.hints__title {
  margin: 0 0 var(--pp-space-3);
  font-size: var(--pp-text-sm);
  color: var(--pp-ink-3);
}

.hints__list {
  display: flex;
  flex-wrap: wrap;
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
  cursor: pointer;
  transition: border-color var(--pp-dur) var(--pp-ease), color var(--pp-dur) var(--pp-ease);
}

.hint:hover {
  border-color: var(--pp-line-hover);
  color: var(--pp-ink);
}

.thread {
  margin: 0 0 var(--pp-space-5);
  padding: 0;
  list-style: none;
}

.thread__item {
  padding: var(--pp-space-4) 0;
  border-top: 1px solid var(--pp-line);
}

.thread__item:first-child {
  border-top: 0;
  padding-top: 0;
}

.thread__question {
  margin: 0;
  font-size: var(--pp-text-base);
  font-weight: var(--pp-weight-medium);
  color: var(--pp-ink);
}

.thread__answer {
  margin: 0;
  font-size: var(--pp-text-base);
  line-height: 1.75;
  color: var(--pp-ink-2);
  white-space: pre-wrap;
}

.thread__note {
  margin: 0 0 var(--pp-space-2);
  font-size: var(--pp-text-xs);
  color: var(--pp-warning);
}

.thread__error {
  margin-top: var(--pp-space-4);
}

/* 引用标记:上标小字,与下方的依据一一对应 */
.cite {
  margin: 0 2px;
  color: var(--pp-accent-deep);
  font-family: var(--pp-font-mono);
  font-size: var(--pp-text-xs);
  font-weight: var(--pp-weight-semibold);
}

.sources {
  margin-top: var(--pp-space-4);
  padding-top: var(--pp-space-3);
  border-top: 1px solid var(--pp-line);
}

.sources__title {
  margin: 0 0 var(--pp-space-2);
  font-size: var(--pp-text-xs);
  color: var(--pp-ink-3);
}

.source__summary {
  cursor: pointer;
  font-size: var(--pp-text-sm);
  color: var(--pp-ink-2);
}

.source__excerpt {
  margin: var(--pp-space-2) 0 0;
  padding-left: var(--pp-space-4);
  font-size: var(--pp-text-sm);
  line-height: 1.7;
  color: var(--pp-ink-3);
}

.askbar {
  display: flex;
  gap: var(--pp-space-3);
}

@media (max-width: 980px) {
  .askbar {
    flex-direction: column;
  }
}
</style>
