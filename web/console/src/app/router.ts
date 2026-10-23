import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../features/auth/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: { name: 'workspace' },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../features/auth/views/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/app/conversations/:conversationId?',
    name: 'workspace',
    component: () => import('../features/chat/views/WorkspaceView.vue'),
  },
  {
    path: '/app/tasks/:taskId',
    name: 'task',
    component: () => import('../features/tasks/views/TaskDetailView.vue'),
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'catchAll',
    redirect: { name: 'workspace' },
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  // 给 ensureSession 加超时，避免 API 挂起阻塞导航
  const loggedIn = await Promise.race([
    auth.ensureSession(),
    new Promise<boolean>((resolve) => setTimeout(() => resolve(false), 3000)),
  ])
  if (!loggedIn && !(to.meta as { public?: boolean }).public) {
    return {
      name: 'login',
      query: to.fullPath === '/' ? {} : { redirect: to.fullPath },
    }
  }
  if (loggedIn && to.name === 'login') {
    return { name: 'workspace' }
  }
  return true
})
