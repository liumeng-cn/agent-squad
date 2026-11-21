export type RbacStatus = 'ACTIVE' | 'DISABLED'
export type MemberStatus = RbacStatus | 'UNASSIGNED'
export type PermissionCode = 'management:access' | 'management:user:read' | 'management:user:manage' | 'management:role:read' | 'management:role:manage' | 'management:permission:read'
export interface Access { canAccess: boolean; permissionCodes: PermissionCode[] }
export interface Query { keyword: string; status: MemberStatus | null; current: number; size: number }
export interface Role {
  roleId: string; code: string; name: string; description: string; status: RbacStatus; builtIn: boolean
  version: number; appliedVersion: number | null; memberCount: number; lastUpdateTime: string
  draftPermissionCodes: PermissionCode[] | null; appliedPermissionCodes: PermissionCode[] | null
}
export interface Member {
  userId: string; username: string; displayName: string; phone: string; status: MemberStatus
  version: number; roleNames: string; roleIds: string[]; lastUpdateTime: string
}
export interface Permission { code: PermissionCode; name: string; module: string; description: string }
export interface RoleDraft { code: string; name: string; description: string; permissionCodes: PermissionCode[] }
