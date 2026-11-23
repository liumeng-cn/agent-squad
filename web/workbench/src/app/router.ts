import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useManagementStore } from '../features/management/stores/management'
import { useAuthStore } from '../features/auth/stores/auth'
import type { PermissionCode } from '../features/management/model/management'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    /** 管理页所属分区 */
    managementSection?: string
    /** 侧栏高亮与菜单授权使用的页面路径；编辑页指向所属列表页 */
    menuPath?: string
    /** 进入页面所需的功能权限 */
    permission?: PermissionCode
    /** 编辑页以 new 作为 ID 新建资源时额外需要的功能权限 */
    createPermission?: PermissionCode
  }
}

const managementView = () => import('../features/management/views/ManagementView.vue')

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: { name: 'chat' } },
  { path: '/login', alias: '/v2/login', name: 'login', component: () => import('../features/auth/views/AuthView.vue'), meta: { public: true } },
  { path: '/register', alias: '/v2/register', name: 'register', component: () => import('../features/auth/views/AuthView.vue'), meta: { public: true } },
  { path: '/chat/:convId', name: 'chat-session', component: () => import('../features/chat/views/ChatView.vue') },
  { path: '/chat', alias: '/app/squad-v2', name: 'chat', component: () => import('../features/chat/views/ChatView.vue') },
  { path: '/management', redirect: '/management/roles' },
  { path: '/management/users', component: managementView, meta: { managementSection: 'users', menuPath: '/management/users', permission: 'management:user:query' } },
  { path: '/management/roles', component: managementView, meta: { managementSection: 'roles', menuPath: '/management/roles', permission: 'management:role:query' } },
  { path: '/management/permissions', component: managementView, meta: { managementSection: 'permissions', menuPath: '/management/permissions', permission: 'management:permission:query' } },
  {
    path: '/management/mcp-servers',
    name: 'management-mcp-servers',
    component: () => import('../features/management/views/McpServerListView.vue'),
    meta: { managementSection: 'mcp-servers', menuPath: '/management/mcp-servers', permission: 'management:mcp:query' },
  },
  {
    path: '/management/mcp-servers/:id',
    name: 'management-mcp-server',
    component: () => import('../features/management/views/McpServerEditView.vue'),
    meta: { managementSection: 'mcp-servers', menuPath: '/management/mcp-servers', permission: 'management:mcp:query', createPermission: 'management:mcp:edit' },
  },
  {
    path: '/management/tools',
    name: 'management-tools',
    component: () => import('../features/management/views/ToolListView.vue'),
    meta: { managementSection: 'tools', menuPath: '/management/tools', permission: 'management:tool:query' },
  },
  {
    path: '/management/tools/:id',
    name: 'management-tool',
    component: () => import('../features/management/views/ToolEditView.vue'),
    meta: { managementSection: 'tools', menuPath: '/management/tools', permission: 'management:tool:query', createPermission: 'management:tool:edit' },
  },
  {
    path: '/management/agents',
    name: 'management-agents',
    component: () => import('../features/management/views/AgentListView.vue'),
    meta: { managementSection: 'agents', menuPath: '/management/agents', permission: 'management:agent:query' },
  },
  {
    path: '/management/agents/:id',
    name: 'management-agent',
    component: () => import('../features/management/views/AgentEditView.vue'),
    meta: { managementSection: 'agents', menuPath: '/management/agents', permission: 'management:agent:query', createPermission: 'management:agent:edit' },
  },
  { path: '/:pathMatch(.*)*', redirect: { name: 'chat' } },
]

export const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(async (to) => {
  if (to.meta.public) {
    document.title = `${to.name === 'register' ? '注册' : '登录'} · Squad`
    return true
  }
  if (!useAuthStore().user && !await useAuthStore().ensureSession()) {
    return { name: 'login' }
  }

  if (to.meta.managementSection) {
    const access = useManagementStore()
    try {
      if (!await access.refresh()) {
        return { name: 'chat' }
      }
    } catch {
      return { name: 'chat' }
    }

    // 菜单未授权时回到首个有权页面；编辑页还需要对应的功能权限，缺少时回到所属列表页。
    const menuPath = to.meta.menuPath ?? to.path
    if (!access.hasMenu(menuPath)) {
      const first = access.pages()[0]
      return first?.routePath || { name: 'chat' }
    }
    const isListPage = to.path === menuPath
    const creating = to.params.id === 'new'
    const required = creating ? to.meta.createPermission : to.meta.permission
    if (!isListPage && required && !access.can(required)) {
      return menuPath
    }
    document.title = '管理平台 · Squad'
  }
  return true
})
