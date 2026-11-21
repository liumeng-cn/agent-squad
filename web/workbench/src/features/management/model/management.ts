export type RbacStatus = 'ACTIVE' | 'DISABLED'
export type MemberStatus = RbacStatus | 'UNASSIGNED'
export type PermissionCode = 'management:user:query' | 'management:user:assign-role' | 'management:user:enable' | 'management:user:disable' | 'management:role:query' | 'management:role:create' | 'management:role:update' | 'management:role:apply' | 'management:role:enable' | 'management:role:disable' | 'management:permission:query'
export interface Access { canAccess: boolean; permissionCodes: PermissionCode[]; menus: Menu[] }
export interface Query { keyword: string; status: MemberStatus | null; current: number; size: number }
export interface Role {
  roleId: string; code: string; name: string; description: string; status: RbacStatus; builtIn: boolean
  version: number; appliedVersion: number | null; memberCount: number; lastUpdateTime: string
  draftMenuIds: string[]; appliedMenuIds: string[]
  draftPermissionCodes: PermissionCode[] | null; appliedPermissionCodes: PermissionCode[] | null
}
export interface Member {
  userId: string; username: string; displayName: string; phone: string; status: MemberStatus
  version: number; roleNames: string; roleIds: string[]; lastUpdateTime: string
}
export interface Permission { code: PermissionCode; name: string; module: string; description: string }
export interface RoleDraft { code: string; name: string; description: string }

export interface MenuFunction { code: PermissionCode; name: string; menuId: string; status: RbacStatus; selectable: boolean; disabledReason: string | null }
export interface Menu { menuId: string; parentMenuId: string | null; code: string; name: string; type: 'DIRECTORY' | 'PAGE'; routeName: string | null; routePath: string | null; status: RbacStatus; selectable: boolean; disabledReason: string | null; children: Menu[]; functions: MenuFunction[] }
