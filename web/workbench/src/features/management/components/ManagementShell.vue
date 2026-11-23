<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../../auth/stores/auth'
import { useManagementStore } from '../stores/management'
import type { Menu } from '../model/management'
import '../management.css'

/**
 * 管理平台外壳：顶栏、按菜单目录分组的侧栏、面包屑和内容区。
 * 侧栏只展示当前用户已授权的页面；高亮依据路由 meta.menuPath，编辑页也能对应到所属菜单。
 */
const props = defineProps<{ current?: string }>()

interface NavLink { menuId: string; name: string; path: string }
interface NavGroup { key: string; title: string; links: NavLink[] }

const route = useRoute()
const access = useManagementStore()
const auth = useAuthStore()

const activePath = computed(() => String(route.meta.menuPath ?? route.path))

function collectPages(nodes: Menu[]): NavLink[] {
  const links: NavLink[] = []
  for (const node of nodes) {
    if (node.type === 'PAGE' && node.routePath) {
      links.push({ menuId: node.menuId, name: node.name, path: node.routePath })
    }
    links.push(...collectPages(node.children))
  }
  return links
}

/** 根目录直接挂页面时归入“管理平台”分组；只有一层目录包裹时用子目录作为分组标题。 */
const groups = computed<NavGroup[]>(() => {
  const result: NavGroup[] = []
  const loose: NavLink[] = []

  for (const node of access.menus) {
    if (node.type === 'PAGE') {
      loose.push(...collectPages([node]))
      continue
    }
    const directPages = collectPages(node.children.filter(child => child.type === 'PAGE'))
    if (directPages.length) {
      result.push({ key: node.menuId, title: node.name, links: directPages })
    }
    for (const child of node.children.filter(item => item.type === 'DIRECTORY')) {
      const links = collectPages(child.children)
      if (links.length) {
        result.push({ key: child.menuId, title: child.name, links })
      }
    }
  }

  if (loose.length) {
    result.unshift({ key: 'loose', title: '管理平台', links: loose })
  }
  return result
})

const activeGroup = computed(() => groups.value.find(group => group.links.some(link => link.path === activePath.value)))
const activeLink = computed(() => activeGroup.value?.links.find(link => link.path === activePath.value))

const crumbs = computed(() => {
  const items: { label: string; path?: string }[] = []
  if (activeGroup.value) {
    items.push({ label: activeGroup.value.title })
  }
  if (activeLink.value) {
    items.push({ label: activeLink.value.name, path: props.current ? activeLink.value.path : undefined })
  }
  if (props.current) {
    items.push({ label: props.current })
  }
  return items
})
</script>

<template>
  <div class="management-platform">
    <header class="management-top">
      <RouterLink class="management-brand" :to="groups[0]?.links[0]?.path ?? '/management'">
        <img src="/brand/squad-icon.svg" alt="" width="26" height="26" />
        <span class="management-brand-name">Agent Squad</span>
        <span class="management-brand-sub">管理平台</span>
      </RouterLink>
      <div class="management-top-user">
        <span class="management-top-name">{{ auth.user?.effectiveDisplayName }}</span>
        <RouterLink to="/chat">返回聊天</RouterLink>
      </div>
    </header>

    <div class="management-layout">
      <aside class="management-sidebar">
        <nav class="management-nav" aria-label="管理菜单">
          <div v-for="group in groups" :key="group.key" class="management-nav-group">
            <div class="management-nav-title">{{ group.title }}</div>
            <RouterLink
              v-for="link in group.links"
              :key="link.menuId"
              class="management-nav-link"
              :to="link.path"
              :aria-current="link.path === activePath ? 'page' : undefined"
            >
              {{ link.name }}
            </RouterLink>
          </div>
        </nav>
      </aside>

      <main class="management-main">
        <div class="management-content">
          <nav v-if="crumbs.length" class="management-breadcrumb" aria-label="当前位置">
            <ol>
              <li v-for="(crumb, index) in crumbs" :key="index">
                <RouterLink v-if="crumb.path" :to="crumb.path">{{ crumb.label }}</RouterLink>
                <span v-else>{{ crumb.label }}</span>
              </li>
            </ol>
          </nav>
          <slot />
        </div>
      </main>
    </div>
  </div>
</template>
