import type { Page } from '../../../shared/api/page'
import { apiDelete, apiGet, apiPatch, apiPost } from '../../../shared/http/http'
import type { ConvSummary } from '../model/conv'

export const listConvs = (current = 1, size = 30, archived = false) =>
  apiPost<Page<ConvSummary>>('/api/v2/chat/convs/page', { current, size, archived })
export const readConv = (convId: string) =>
  apiGet<ConvSummary>(`/api/v2/chat/convs/${encodeURIComponent(convId)}`)
export const renameConv = (convId: string, title: string, version: number) =>
  apiPatch<ConvSummary>(`/api/v2/chat/convs/${encodeURIComponent(convId)}`, { title, version })
export const deleteConv = (convId: string) =>
  apiDelete<void>(`/api/v2/chat/convs/${encodeURIComponent(convId)}`)
export const archiveConv = (convId: string) =>
  apiPost<void>(`/api/v2/chat/convs/${encodeURIComponent(convId)}/archive`)
export const unarchiveConv = (convId: string) =>
  apiPost<void>(`/api/v2/chat/convs/${encodeURIComponent(convId)}/unarchive`)
