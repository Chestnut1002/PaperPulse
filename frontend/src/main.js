import { createApp } from 'vue'

import App from './App.vue'
import { router } from './router'

// 顺序有讲究:先重置,再设计令牌与 Element Plus 覆盖,最后共享组件类。
// Element Plus 的样式由 unplugin 按需注入(见 vite.config.js),加载顺序晚于这里,
// 所以 theme.css 里的覆盖用 :root:root 提高了特异性 —— 与顺序解耦。
import './style.css'
import './styles/theme.css'
import './styles/components.css'

// Element Plus 的组件与 ElMessage 等 API 由 unplugin 自动引入,
// 所以这里没有 app.use(ElementPlus);中文 locale 在 App.vue 用 el-config-provider 指定。
createApp(App).use(router).mount('#app')
