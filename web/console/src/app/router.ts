import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../features/auth/stores/auth'

const routes: RouteRecordRaw[] = [
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
    redirect: { name: 'workspace' },
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  const loggedIn = await auth.ensureSession()
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
