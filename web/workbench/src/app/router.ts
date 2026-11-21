import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useManagementStore } from '../features/management/stores/management'
import { useAuthStore } from '../features/auth/stores/auth'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: { name: 'chat' } },
  { path: '/login', alias: '/v2/login', name: 'login', component: () => import('../features/auth/views/AuthView.vue'), meta: { public: true } },
  { path: '/register', alias: '/v2/register', name: 'register', component: () => import('../features/auth/views/AuthView.vue'), meta: { public: true } },
  { path: '/chat/:convId', name: 'chat-session', component: () => import('../features/chat/views/ChatView.vue') },
  { path: '/chat', alias: '/app/squad-v2', name: 'chat', component: () => import('../features/chat/views/ChatView.vue') },
  { path: '/management', redirect: '/management/roles' },
  { path: '/management/users', component: () => import('../features/management/views/ManagementView.vue'), meta: { managementSection: 'users', permission: 'management:user:query' } },
  { path: '/management/roles', component: () => import('../features/management/views/ManagementView.vue'), meta: { managementSection: 'roles', permission: 'management:role:query' } },
  { path: '/management/permissions', component: () => import('../features/management/views/ManagementView.vue'), meta: { managementSection: 'permissions', permission: 'management:permission:query' } },
  { path: '/:pathMatch(.*)*', redirect: { name: 'chat' } },
]
export const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach(async (to) => {
  if (to.meta.public) { document.title = `${to.name === 'register' ? '注册' : '登录'} · Squad`; return true }
  if (!useAuthStore().user && !await useAuthStore().ensureSession()) return { name: 'login' }
  if (to.meta.managementSection) {
    const access = useManagementStore()
    try { if (!await access.refresh()) return { name: 'chat' } }
    catch { return { name: 'chat' } }
    if (!access.hasMenu(to.path)) {
      const first = access.pages()[0]
      return first?.routePath || { name: 'chat' }
    }
    document.title = '管理平台 · Squad'
  }
  return true
})
