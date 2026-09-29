import { createApp } from 'vue'

import App from './App.vue'
import { router } from './router'
import './style.css'

// Element Plus 的组件、样式与 ElMessage 等 API 由 unplugin 按需自动引入
// (见 vite.config.js),所以这里不再 app.use(ElementPlus);
// 中文 locale 在 App.vue 用 el-config-provider 指定。
createApp(App).use(router).mount('#app')
