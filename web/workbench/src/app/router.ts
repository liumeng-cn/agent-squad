import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../features/auth/stores/auth'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: { name: 'chat' } },
  { path: '/login', alias: '/v2/login', name: 'login', component: () => import('../features/auth/views/AuthView.vue'), meta: { public: true } },
  { path: '/register', alias: '/v2/register', name: 'register', component: () => import('../features/auth/views/AuthView.vue'), meta: { public: true } },
  { path: '/chat/:convId', name: 'chat-session', component: () => import('../features/chat/views/ChatView.vue') },
  { path: '/chat', alias: '/app/squad-v2', name: 'chat', component: () => import('../features/chat/views/ChatView.vue') },
  { path: '/:pathMatch(.*)*', redirect: { name: 'chat' } },
]
export const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach(async (to) => {
  if (to.meta.public) { document.title = `${to.name === 'register' ? '注册' : '登录'} · Squad`; return true }
  if (!useAuthStore().user && !await useAuthStore().ensureSession()) return { name: 'login' }
  return true
})
