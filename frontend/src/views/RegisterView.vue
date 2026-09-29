<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { login, register } from '../api/auth'
import { authStore } from '../stores/auth'

const router = useRouter()

const formRef = ref()
const submitting = ref(false)

const form = reactive({ username: '', email: '', password: '', confirmPassword: '' })

/** 后端逐字段校验失败的提示,绑定到对应表单项。 */
const serverErrors = reactive({})

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
  Object.keys(serverErrors).forEach(clearServerError)
  try {
    await register({
      username: form.username,
      email: form.email,
      password: form.password,
    })

    // 注册接口不返回 token,紧接着用同一份凭据登录一次 —— 省掉用户再手输一遍。
    // 单独 catch:注册已经成功了,自动登录失败不该让用户以为注册没成功。
    try {
      authStore.signIn(await login({ username: form.username, password: form.password }))
      ElMessage.success('注册成功')
      await router.replace({ name: 'home' })
    } catch {
      ElMessage.success('注册成功,请登录')
      await router.replace({ name: 'login' })
    }
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
      <h1 class="auth-card__title">注册</h1>
      <p class="auth-card__subtitle">创建你的 PaperPulse 账号</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" prop="username" :error="serverErrors.username">
          <el-input
            v-model="form.username"
            placeholder="3~50 位字母、数字或下划线"
            @input="clearServerError('username')"
          />
        </el-form-item>

        <el-form-item label="邮箱" prop="email" :error="serverErrors.email">
          <el-input
            v-model="form.email"
            placeholder="请输入邮箱"
            @input="clearServerError('email')"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password" :error="serverErrors.password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="6~72 位"
            @input="clearServerError('password')"
          />
        </el-form-item>

        <!-- 确认密码只在前端校验,后端没有这个字段,因此不绑服务端错误 -->
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            placeholder="请再次输入密码"
          />
        </el-form-item>

        <el-button
          type="primary"
          native-type="submit"
          :loading="submitting"
          class="auth-card__submit"
        >
          注册
        </el-button>
      </el-form>

      <p class="auth-card__footer">
        已有账号?
        <router-link :to="{ name: 'login' }">去登录</router-link>
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
