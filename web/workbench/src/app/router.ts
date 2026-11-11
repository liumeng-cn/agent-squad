import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../features/auth/stores/auth'
import type { AccountRole } from '../shared/api/types'

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
    path: '/app/native-chat',
    name: 'native-chat',
    component: () => import('../features/native-chat/views/NativeChatView.vue'),
  },
  {
    path: '/app/tasks/:taskId',
    name: 'task',
    component: () => import('../features/tasks/views/TaskDetailView.vue'),
  },
  {
    path: '/app/skills',
    name: 'skills',
    component: () => import('../features/skills/views/SkillsView.vue'),
  },
  {
    path: '/app/settings/connections',
    name: 'connections',
    component: () => import('../features/connections/views/ConnectionsView.vue'),
  },
  {
    path: '/app/settings/memory',
    name: 'memory',
    component: () => import('../features/memory/views/MemorySettingsView.vue'),
  },
  {
    path: '/app/account/password',
    name: 'changePassword',
    component: () => import('../features/auth/views/ChangePasswordView.vue'),
  },
  {
    path: '/app/admin/users',
    name: 'adminUsers',
    component: () => import('../features/admin/views/AdminUsersView.vue'),
    meta: { role: 'ADMIN' },
  },
  {
    path: '/app/admin/workers',
    name: 'workers',
    component: () => import('../features/workers/views/WorkerDefinitionsView.vue'),
    meta: { role: 'ADMIN' },
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
  // 临时密码状态下除改密页外一律回到改密流程，避免进入业务页面受 403 拦截。
  if (loggedIn && auth.mustChangePassword && to.name !== 'changePassword') {
    return { name: 'changePassword' }
  }
  if (loggedIn && to.name === 'login') {
    return { name: 'workspace' }
  }
  const requiredRole = to.meta.role as AccountRole | undefined
  if (loggedIn && requiredRole && !auth.user?.roles.includes(requiredRole)) {
    return { name: 'workspace' }
  }
  return true
})
