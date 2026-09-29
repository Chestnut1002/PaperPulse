<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { login } from '../api/auth'
import { authStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()

const formRef = ref()
const submitting = ref(false)

const form = reactive({ username: '', password: '' })

/** 后端逐字段校验失败的提示,绑定到对应表单项。 */
const serverErrors = reactive({})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

function clearServerError(field) {
  delete serverErrors[field]
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  Object.keys(serverErrors).forEach(clearServerError)
  try {
    const result = await login({ username: form.username, password: form.password })
    authStore.signIn(result)
    // 被守卫拦下前想去的页面优先,否则回首页
    await router.replace(route.query.redirect || { name: 'home' })
  } catch (error) {
    Object.assign(serverErrors, error.fieldErrors ?? {})
    ElMessage.error(error.message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <h1 class="auth-card__title">PaperPulse</h1>
      <p class="auth-card__subtitle">登录你的账号</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" prop="username" :error="serverErrors.username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            @input="clearServerError('username')"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password" :error="serverErrors.password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            @input="clearServerError('password')"
          />
        </el-form-item>

        <el-button
          type="primary"
          native-type="submit"
          :loading="submitting"
          class="auth-card__submit"
        >
          登录
        </el-button>
      </el-form>

      <p class="auth-card__footer">
        还没有账号?
        <router-link :to="{ name: 'register' }">去注册</router-link>
      </p>
    </el-card>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: 24px;
}

.auth-card {
  width: 100%;
  max-width: 380px;
}

.auth-card__title {
  margin: 0;
  font-size: 24px;
  text-align: center;
}

.auth-card__subtitle {
  margin: 8px 0 24px;
  color: #909399;
  font-size: 14px;
  text-align: center;
}

.auth-card__submit {
  width: 100%;
}

.auth-card__footer {
  margin: 16px 0 0;
  color: #606266;
  font-size: 14px;
  text-align: center;
}
</style>
