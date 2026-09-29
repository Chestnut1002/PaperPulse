<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { login, register } from '../api/auth'
import AuthShell from '../components/AuthShell.vue'
import { authStore } from '../stores/auth'

const router = useRouter()

const formRef = ref()
const submitting = ref(false)

/** 接口级错误(用户名被占用 / 网络不可达):顶部一条提示,不用浮层。 */
const formError = ref('')
/** 字段级错误:贴到对应输入框下方。 */
const serverErrors = reactive({})

const form = reactive({ username: '', email: '', password: '', confirmPassword: '' })

/** 本地校验规则与后端 RegisterRequest 的约束保持一致 —— 能在本地拦下的就不必往返一次。 */
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度需在 3~50 之间', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 72, message: '密码长度需在 6~72 之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        callback(value === form.password ? undefined : new Error('两次输入的密码不一致'))
      },
      trigger: 'blur',
    },
  ],
}

function clearServerError(field) {
  delete serverErrors[field]
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  formError.value = ''
  Object.keys(serverErrors).forEach(clearServerError)
  try {
    await register({ username: form.username, email: form.email, password: form.password })

    // 注册接口不返回 token,紧接着用同一份凭据登录一次 —— 省掉用户再手输一遍。
    // 单独 try:注册已经成功了,自动登录失败不该让用户以为注册没成功。
    try {
      authStore.signIn(await login({ username: form.username, password: form.password }))
      await router.replace({ name: 'home' })
    } catch {
      // 提示挂在登录页上,导航走了也还看得见
      await router.replace({ name: 'login', query: { registered: '1' } })
    }
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
    <h1 class="auth__title">创建账号</h1>
    <p class="auth__sub">两步之后就能收到专属推荐</p>

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
          placeholder="3~50 位字母、数字或下划线"
          autocomplete="username"
          @input="clearServerError('username')"
        />
      </el-form-item>

      <el-form-item label="邮箱" prop="email" :error="serverErrors.email">
        <el-input
          v-model="form.email"
          size="large"
          placeholder="you@example.com"
          autocomplete="email"
          @input="clearServerError('email')"
        />
      </el-form-item>

      <el-form-item label="密码" prop="password" :error="serverErrors.password">
        <el-input
          v-model="form.password"
          type="password"
          size="large"
          show-password
          placeholder="6~72 位"
          autocomplete="new-password"
          @input="clearServerError('password')"
        />
      </el-form-item>

      <!-- 确认密码只在前端校验,后端没有这个字段,因此不绑服务端错误 -->
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          size="large"
          show-password
          placeholder="请再次输入密码"
          autocomplete="new-password"
        />
      </el-form-item>

      <el-button
        type="primary"
        size="large"
        native-type="submit"
        :loading="submitting"
        class="submit"
      >
        注册并登录
      </el-button>
    </el-form>

    <p class="auth__switch">
      已有账号?
      <router-link class="link" :to="{ name: 'login' }">去登录</router-link>
    </p>
  </AuthShell>
</template>

<style scoped>
.submit {
  width: 100%;
}
</style>
