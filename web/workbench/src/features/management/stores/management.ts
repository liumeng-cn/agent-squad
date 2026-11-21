import { defineStore } from 'pinia'
import { managementAccess } from '../api/managementApi'
import { useAuthStore } from '../../auth/stores/auth'
import type { PermissionCode } from '../model/management'
export const useManagementStore = defineStore('management-access', {
  state: () => ({ canAccess: false, permissionCodes: [] as PermissionCode[] }),
  actions: {
    can(code: PermissionCode) { return this.canAccess && this.permissionCodes.includes(code) },
    clear() { this.canAccess = false; this.permissionCodes = [] },
    async refresh() {
      const account = useAuthStore().user?.userId
      try {
        const result = await managementAccess()
        if (account !== useAuthStore().user?.userId) { this.clear(); return false }
        this.canAccess = result.canAccess; this.permissionCodes = result.permissionCodes
        return this.canAccess
      } catch (error) { this.clear(); throw error }
    },
  },
})
