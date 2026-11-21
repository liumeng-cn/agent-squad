import type { Page } from '../../../shared/api/page'
import { apiDelete, apiGet, apiPatch, apiPost } from '../../../shared/http/http'
import type { ConvSummary } from '../model/conv'

export const listConvs = (current = 1, size = 30, archived = false) =>
  apiPost<Page<ConvSummary>>('/v2-server/api/v2/chat/convs/page', { current, size, archived })
export const readConv = (convId: string) =>
  apiGet<ConvSummary>(`/v2-server/api/v2/chat/convs/${encodeURIComponent(convId)}`)
export const renameConv = (convId: string, title: string, version: number) =>
  apiPatch<ConvSummary>(`/v2-server/api/v2/chat/convs/${encodeURIComponent(convId)}`, { title, version })
export const deleteConv = (convId: string) =>
  apiDelete<void>(`/v2-server/api/v2/chat/convs/${encodeURIComponent(convId)}`)
export const archiveConv = (convId: string) =>
  apiPost<void>(`/v2-server/api/v2/chat/convs/${encodeURIComponent(convId)}/archive`)
export const unarchiveConv = (convId: string) =>
  apiPost<void>(`/v2-server/api/v2/chat/convs/${encodeURIComponent(convId)}/unarchive`)
