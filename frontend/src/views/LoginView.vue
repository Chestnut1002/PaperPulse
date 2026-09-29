<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { login } from '../api/auth'
import AuthShell from '../components/AuthShell.vue'
import { authStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()

const formRef = ref()
const submitting = ref(false)

/** 接口级错误(凭据错误 / 网络不可达):顶部一条提示,不用浮层。 */
const formError = ref('')
/** 字段级错误:贴到对应输入框下方。 */
const serverErrors = reactive({})
/** 注册页跳转过来时带的提示。 */
const justRegistered = ref(route.query.registered === '1')

const form = reactive({ username: '', password: '' })

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
  formError.value = ''
  justRegistered.value = false
  Object.keys(serverErrors).forEach(clearServerError)
  try {
    authStore.signIn(await login({ username: form.username, password: form.password }))
    // 被守卫拦下前想去的页面优先,否则回首页
    await router.replace(route.query.redirect || { name: 'home' })
  } catch (error) {
    Object.assign(serverErrors, error.fieldErrors ?? {})
    formError.value = error.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthShell>
    <h1 class="auth__title">欢迎回来</h1>
    <p class="auth__sub">登录后继续你的论文流</p>

    <p v-if="justRegistered" class="notice" role="status">注册成功,请登录</p>
    <p v-if="formError" class="alert" role="alert">{{ formError }}</p>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="submit"
    >
      <el-form-item label="用户名" prop="username" :error="serverErrors.username">
        <el-input
          v-model="form.username"
          size="large"
          placeholder="请输入用户名"
          autocomplete="username"
          @input="clearServerError('username')"
        />
      </el-form-item>

      <el-form-item label="密码" prop="password" :error="serverErrors.password">
        <el-input
          v-model="form.password"
          type="password"
          size="large"
          show-password
          placeholder="请输入密码"
          autocomplete="current-password"
          @input="clearServerError('password')"
        />
      </el-form-item>

      <el-button
        type="primary"
        size="large"
        native-type="submit"
        :loading="submitting"
        class="submit"
      >
        登录
      </el-button>
    </el-form>

    <p class="auth__switch">
      还没有账号?
      <router-link class="link" :to="{ name: 'register' }">去注册</router-link>
    </p>
  </AuthShell>
</template>

<style scoped>
.submit {
  width: 100%;
}
</style>
