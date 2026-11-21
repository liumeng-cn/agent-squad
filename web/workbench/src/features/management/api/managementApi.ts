import { apiGet, apiPost, apiPut } from '../../../shared/http/http'
import type { Page } from '../../../shared/api/page'
import type { Access, Query, Role, Member, Permission, RoleDraft, RbacStatus, Menu, PermissionCode } from '../model/management'
const base = '/user-server/api/v2/users/management'
export const managementAccess = () => apiGet<Access>('/user-server/api/v2/users/me/management-access')
export const rolePage = (query: Query) => apiPost<Page<Role>>(`${base}/roles/page`, query)
export const memberPage = (query: Query) => apiPost<Page<Member>>(`${base}/users/page`, query)
export const permissionPage = (query: Query) => apiPost<Page<Permission>>(`${base}/permissions/page`, query)
export const roleDetail = (id: string) => apiGet<Role>(`${base}/roles/${encodeURIComponent(id)}`)
export const roleOptions = () => apiGet<Role[]>(`${base}/users/role-options`)
export const createRole = (body: RoleDraft) => apiPost<Role>(`${base}/roles`, body)
export const updateRole = (role: Role, body: RoleDraft) => apiPut<Role>(`${base}/roles/${role.roleId}`, { ...body, version: role.version })
export const applyRole = (role: Role) => apiPost<Role>(`${base}/roles/${role.roleId}/apply`, { version: role.version })
export const setRoleStatus = (role: Role, status: RbacStatus) => apiPost<Role>(`${base}/roles/${role.roleId}/${status === 'ACTIVE' ? 'enable' : 'disable'}`, { version: role.version })
export const findMember = (phone: string) => apiGet<Member>(`${base}/users/by-phone?phone=${encodeURIComponent(phone)}`)
export const saveMemberRoles = (member: Member, roleIds: string[]) => apiPut<Member>(`${base}/users/${member.userId}/roles`, { roleIds, version: member.version })
export const setMemberStatus = (member: Member, status: RbacStatus) => apiPost<Member>(`${base}/users/${member.userId}/${status === 'ACTIVE' ? 'enable' : 'disable'}`, { version: member.version })

export const authorizationTree = () => apiGet<Menu[]>(`${base}/authorization-tree`)
export const roleAuthorization = (id: string) => apiGet<Role>(`${base}/roles/${id}/authorization`)
export const saveAuthorization = (role: Role, menuIds: string[], permissionCodes: PermissionCode[]) => apiPut<Role>(`${base}/roles/${role.roleId}/authorization`, { menuIds, permissionCodes, version: role.version })
