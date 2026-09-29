import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { defineConfig } from 'vitest/config'

// 后端地址。默认本机 8080;后端起在别的端口时用环境变量覆盖,例如
//   BACKEND_ORIGIN=http://localhost:8081 npm run dev
const backendOrigin = process.env.BACKEND_ORIGIN || 'http://localhost:8080'

// dev 与 preview 都配代理:前端代码只认相对路径 /api,由 Vite 转发给后端。
// 浏览器视角下同源,所以开发阶段后端不需要任何 CORS 配置。
const proxy = {
  '/api': { target: backendOrigin },
}

// Element Plus 按需引入:只打包实际用到的组件、样式与 API。
// 全量 `app.use(ElementPlus)` 会让首屏 JS 到 1MB(gzip 326KB),按需引入后各页面按需加载。
// dts: false —— 项目是 JavaScript,不需要生成类型声明文件。
const elementPlus = { resolvers: [ElementPlusResolver()], dts: false }

export default defineConfig({
  plugins: [
    vue(),
    // 自动引入 ElMessage / ElMessageBox 这类"服务式"API
    AutoImport(elementPlus),
    // 自动注册模板里用到的组件(含 v-loading 等指令)
    Components(elementPlus),
  ],
  server: { proxy },
  preview: { proxy },
  test: {
    environment: 'jsdom',
    server: {
      deps: {
        // Element Plus 按需引入会给每个组件注入 .css 的 import。
        // 若被当作外部依赖交给 Node 直接加载,Node 不认识 .css 会直接报错;
        // 内联后交给 Vite 的管线处理就没问题。
        inline: ['element-plus'],
      },
    },
  },
})
