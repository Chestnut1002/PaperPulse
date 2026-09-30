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
      // 首页只用本地数据,秒开 —— 推荐页要等十来秒,不能放在落地的位置
      {
        path: '',
        name: 'home',
        component: () => import('../views/OverviewView.vue'),
        meta: { navLabel: '首页' },
      },
      // 精读是主功能,给它一个独立入口 —— 用户往往手里已经有一篇想读的论文
      {
        path: 'reading',
        name: 'reading-entry',
        component: () => import('../views/ReadingEntryView.vue'),
        meta: { navLabel: '精读' },
      },
      {
        path: 'recommendations',
        name: 'recommendations',
        component: () => import('../views/RecommendView.vue'),
        meta: { navLabel: '今日推荐' },
      },
      // 精读页也不占导航位 —— 它是从某篇论文进去的
      {
        path: 'papers/:paperId/reading',
        name: 'reading',
        component: () => import('../views/PaperReadingView.vue'),
      },
      // 账号信息不做成导航项 —— 从顶栏的用户名进,它不值得占一个菜单位置
      {
        path: 'account',
        name: 'account',
        component: () => import('../views/AccountView.vue'),
      },
      {
        path: 'search',
        name: 'search',
        component: () => import('../views/SearchView.vue'),
        meta: { navLabel: '检索' },
      },
      {
        path: 'library',
        name: 'library',
        component: () => import('../views/LibraryView.vue'),
        meta: { navLabel: '我的论文' },
      },
      {
        path: 'interests',
        name: 'interests',
        component: () => import('../views/InterestView.vue'),
        // 顶栏导航项由路由表驱动,新增页面只要在这里加一个 meta.navLabel
        meta: { navLabel: '兴趣标签' },
      },
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

/** 顶栏要显示的导航项。从路由表推导,避免两处各维护一份。 */
export const navItems = routes[0].children
  .filter((route) => route.meta?.navLabel)
  .map((route) => ({ name: route.name, label: route.meta.navLabel }))

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
