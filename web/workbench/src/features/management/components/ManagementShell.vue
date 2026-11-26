<script setup lang="ts">
import {
  ElConfigProvider,
} from 'element-plus'
import { computed, ref, watch } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../../auth/stores/auth'
import { useManagementStore } from '../stores/management'
import type { Menu } from '../model/management'
import 'element-plus/dist/index.css'
import '../management.css'

/**
 * 管理平台统一外壳：左侧固定导航栏（窄屏自动折叠抽屉）+ 顶部极简毛玻璃栏 + 浅灰主画布。
 */
const props = defineProps<{ current?: string }>()

interface NavLink {
  menuId: string
  name: string
  path: string
}
interface NavGroup {
  key: string
  title: string
  links: NavLink[]
}

const route = useRoute()
const access = useManagementStore()
const auth = useAuthStore()
const drawerOpen = ref(false)

const activePath = computed(() => String(route.meta.menuPath ?? route.path))

watch(activePath, () => {
  drawerOpen.value = false
})

function collectPages(nodes: Menu[]): NavLink[] {
  const links: NavLink[] = []
  for (const node of nodes) {
    if (node.type === 'PAGE' && node.routePath) {
      links.push({
        menuId: node.menuId,
        name: node.name,
        path: node.routePath,
      })
    }
    links.push(...collectPages(node.children))
  }
  return links
}

/**
 * 根节点（如“管理平台”）本身代表整个后台外壳，其直接挂靠的用户/角色/权限页归入“账号与权限”分组，
 * 与“Agent 配置”等子目录平级展示。
 */
const groups = computed<NavGroup[]>(() => {
  const result: NavGroup[] = []
  const loose: NavLink[] = []

  for (const node of access.menus) {
    if (node.type === 'PAGE') {
      loose.push(...collectPages([node]))
      continue
    }
    const directPages = collectPages(
      node.children.filter((child) => child.type === 'PAGE'),
    )
    if (directPages.length) {
      const groupTitle = node.code === 'management' ? '账号与权限' : node.name
      result.push({ key: node.menuId, title: groupTitle, links: directPages })
    }
    for (const child of node.children.filter(
      (item) => item.type === 'DIRECTORY',
    )) {
      const links = collectPages(child.children)
      if (links.length) {
        result.push({ key: child.menuId, title: child.name, links })
      }
    }
  }

  if (loose.length) {
    result.unshift({ key: 'loose', title: '账号与权限', links: loose })
  }
  return result
})

const activeGroup = computed(() =>
  groups.value.find((group) =>
    group.links.some((link) => link.path === activePath.value),
  ),
)
const activeLink = computed(() =>
  activeGroup.value?.links.find((link) => link.path === activePath.value),
)

const topTitle = computed(() => props.current || activeLink.value?.name || '管理平台')
const userInitial = computed(() => {
  const name = auth.user?.effectiveDisplayName?.trim() || '管'
  return name.slice(0, 1).toUpperCase()
})
</script>

<template>
  <ElConfigProvider :locale="zhCn">
    <div class="management-platform mp-app">
      <!-- 窄屏遮罩 -->
      <div v-if="drawerOpen" class="mp-rail-backdrop" @click="drawerOpen = false" />

      <!-- 左侧导航栏 -->
      <aside class="mp-rail" :class="{ 'is-open': drawerOpen }">
        <RouterLink class="mp-rail-brand" :to="groups[0]?.links[0]?.path ?? '/management'">
          <span class="mp-brand-badge">
            <img src="/brand/squad-icon.svg" alt="" width="22" height="22" />
          </span>
          <span class="mp-brand-text">
            <strong class="mp-brand-title">Agent Squad</strong>
            <span class="mp-brand-sub">管理控制台</span>
          </span>
        </RouterLink>

        <nav class="mp-rail-nav" aria-label="管理菜单">
          <div v-for="group in groups" :key="group.key" class="mp-nav-group">
            <div class="mp-nav-group-title">{{ group.title }}</div>
            <RouterLink
              v-for="link in group.links"
              :key="link.menuId"
              :to="link.path"
              class="mp-nav-item"
              :class="{ 'is-active': activePath === link.path }"
            >
              <span class="mp-nav-dot" />
              <span>{{ link.name }}</span>
            </RouterLink>
          </div>
        </nav>

        <footer class="mp-rail-foot">
          <div class="mp-user-pill">
            <span class="mp-user-avatar">{{ userInitial }}</span>
            <div class="mp-user-meta">
              <div class="mp-user-name">{{ auth.user?.effectiveDisplayName || '管理员' }}</div>
              <div class="mp-user-role">{{ activeGroup?.title || '工作台' }}</div>
            </div>
          </div>
          <RouterLink class="mp-back-chat" to="/chat">返回聊天 →</RouterLink>
        </footer>
      </aside>

      <!-- 右侧主区域 -->
      <div class="mp-shell-main">
        <header class="mp-topbar">
          <div class="mp-topbar-left">
            <button
              type="button"
              class="mp-menu-btn"
              aria-label="展开导航菜单"
              @click="drawerOpen = !drawerOpen"
            >
              ☰
            </button>
            <span v-if="activeGroup" class="mp-topbar-group">{{ activeGroup.title }}</span>
            <span v-if="activeGroup" class="mp-topbar-sep">/</span>
            <span class="mp-topbar-title">{{ topTitle }}</span>
          </div>
          <div class="mp-topbar-right">
            <RouterLink class="mp-topbar-chat" to="/chat">返回聊天</RouterLink>
          </div>
        </header>

        <main class="mp-view">
          <slot />
        </main>
      </div>
    </div>
  </ElConfigProvider>
</template>
