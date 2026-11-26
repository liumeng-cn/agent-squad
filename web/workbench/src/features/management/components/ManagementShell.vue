<script setup lang="ts">
import {
  ElAside,
  ElBreadcrumb,
  ElBreadcrumbItem,
  ElConfigProvider,
  ElContainer,
  ElHeader,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElSubMenu,
} from 'element-plus'
import { computed } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../../auth/stores/auth'
import { useManagementStore } from '../stores/management'
import type { Menu } from '../model/management'
import 'element-plus/dist/index.css'
import '../management.css'

/**
 * 管理平台外壳：顶栏、按菜单目录分组的侧栏、面包屑和内容区。
 * 侧栏只展示当前用户已授权的页面；高亮依据路由 meta.menuPath，编辑页也能对应到所属菜单。
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

const activePath = computed(() => String(route.meta.menuPath ?? route.path))

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
 * 与“Agent 配置”等子目录平级展示，避免左侧出现“管理平台”和“Agent 配置”层级错位。
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

const crumbs = computed(() => {
  const items: { label: string; path?: string }[] = []
  if (activeGroup.value) {
    items.push({ label: activeGroup.value.title })
  }
  if (activeLink.value) {
    items.push({
      label: activeLink.value.name,
      path: props.current ? activeLink.value.path : undefined,
    })
  }
  if (props.current) {
    items.push({ label: props.current })
  }
  return items
})
</script>

<template>
  <ElConfigProvider :locale="zhCn">
    <ElContainer class="management-platform" direction="vertical">
      <ElHeader class="management-top" height="56px">
        <RouterLink
          class="management-brand"
          :to="groups[0]?.links[0]?.path ?? '/management'"
        >
          <img src="/brand/squad-icon.svg" alt="" width="26" height="26" />
          <span class="management-brand-name">Agent Squad</span>
          <span class="management-brand-sub">管理平台</span>
        </RouterLink>
        <div class="management-top-user">
          <span class="management-top-name">{{ auth.user?.effectiveDisplayName }}</span>
          <RouterLink class="management-top-back" to="/chat">返回聊天</RouterLink>
        </div>
      </ElHeader>
      <ElContainer class="management-layout">
        <ElAside class="management-sidebar" width="216px">
          <ElMenu
            :default-active="activePath"
            :default-openeds="groups.map(group => group.key)"
            router
            aria-label="管理菜单"
          >
            <ElSubMenu
              v-for="group in groups"
              :key="group.key"
              :index="group.key"
            >
              <template #title>{{ group.title }}</template>
              <ElMenuItem
                v-for="link in group.links"
                :key="link.menuId"
                :index="link.path"
              >
                {{ link.name }}
              </ElMenuItem>
            </ElSubMenu>
          </ElMenu>
        </ElAside>
        <ElMain class="management-main">
          <div class="management-content">
            <ElBreadcrumb
              v-if="crumbs.length"
              class="management-breadcrumb"
              separator="/"
              aria-label="当前位置"
            >
              <ElBreadcrumbItem
                v-for="(crumb, index) in crumbs"
                :key="index"
                :to="crumb.path"
                >{{ crumb.label }}</ElBreadcrumbItem
              >
            </ElBreadcrumb>
            <slot />
          </div>
        </ElMain>
      </ElContainer>
    </ElContainer>
  </ElConfigProvider>
</template>
