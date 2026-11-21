import { defineStore } from 'pinia'
import { managementAccess } from '../api/managementApi'
import { useAuthStore } from '../../auth/stores/auth'
import type { PermissionCode, Menu } from '../model/management'
export const useManagementStore = defineStore('management-access', {
  state: () => ({ canAccess: false, permissionCodes: [] as PermissionCode[], menus: [] as Menu[] }),
  actions: {
    pages(): Menu[] {
      const result: Menu[] = []
      const visit = (nodes: Menu[]) => { for (const node of nodes) { if (node.type === 'PAGE') result.push(node); visit(node.children) } }
      visit(this.menus)
      return result
    },
    hasMenu(path: string) { return this.canAccess && this.pages().some(menu => menu.routePath === path) },
    can(code: PermissionCode) { return this.canAccess && this.permissionCodes.includes(code) },
    clear() { this.canAccess = false; this.permissionCodes = []; this.menus = [] },
    async refresh() {
      const account = useAuthStore().user?.userId
      try {
        const result = await managementAccess()
        if (account !== useAuthStore().user?.userId) { this.clear(); return false }
        this.canAccess = result.canAccess; this.permissionCodes = result.permissionCodes; this.menus = result.menus || []
        return this.canAccess
      } catch (error) { this.clear(); throw error }
    },
  },
})
