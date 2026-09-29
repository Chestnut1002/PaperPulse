import { createRouter, createWebHistory } from 'vue-router'

import { authStore } from '../stores/auth'

// 页面组件一律懒加载:首屏不必下载尚未访问的页面,也顺带避免了模块循环依赖
// (router → view → api/client → router)。
const routes = [
  {
    path: '/',
    component: () => import('../layouts/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'home', component: () => import('../views/HomeView.vue') },
    ],
  },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue') },
  // 未知路径不留白屏
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

/**
 * 未登录访问受保护页 → 去登录页,并记住原本要去哪,登录后跳回来。
 * 已登录还去登录 / 注册页 → 回首页,避免登录后还能看到登录框。
 */
router.beforeEach((to) => {
  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (authStore.isLoggedIn && (to.name === 'login' || to.name === 'register')) {
    return { name: 'home' }
  }
})
