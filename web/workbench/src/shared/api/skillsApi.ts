import { apiGet, apiPost, apiPut } from './http'

export interface SkillFile { path: string; content: string }
export interface SkillVersion {
  skillVersionId: string
  name: string
  version: string
  contentHash: string
  description: string
  status: 'ENABLED' | 'DISABLED' | 'REVOKED'
}
export interface SkillGrant { skillVersionId: string; enabled: boolean; version: number }
export interface SkillDetail { skill: SkillVersion; files: SkillFile[]; grant: SkillGrant }
export interface SkillPage { items: SkillVersion[]; nextCursor?: string | null }

export function listSkills(cursor?: string): Promise<SkillPage> {
  const query = new URLSearchParams({ limit: '30' })
  if (cursor) query.set('cursor', cursor)
  return apiGet(`/api/v1/skills?${query}`)
}
export function getSkill(versionId: string): Promise<SkillDetail> {
  return apiGet(`/api/v1/skills/${encodeURIComponent(versionId)}`)
}
export function importSkill(files: SkillFile[]): Promise<SkillVersion> {
  return apiPost('/api/v1/skills', { files })
}
export function setSkillGrant(detail: SkillDetail, enabled: boolean): Promise<SkillGrant> {
  return apiPut(`/api/v1/skills/${encodeURIComponent(detail.skill.skillVersionId)}/grant`, {
    expectedVersion: detail.grant.version, enabled,
  })
}
export function revokeSkill(detail: SkillDetail): Promise<SkillDetail> {
  return apiPost(`/api/v1/skills/${encodeURIComponent(detail.skill.skillVersionId)}/revoke`, {
    expectedVersion: detail.grant.version,
  })
}
