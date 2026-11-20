import { defineStore } from 'pinia'
import { archiveConv, deleteConv, listConvs, readConv, renameConv } from '../api/convApi'
import type { ConvSummary } from '../model/conv'
import { ApiError, errorMessage } from '../../../shared/http/http'
export const useConvsStore = defineStore('chat-convs', {
  state: () => ({ items: [] as ConvSummary[], current: 0, hasMore: false, loading: false, error: '', generation: 0 }),
  actions: {
    upsert(summary: ConvSummary) {
      const old = this.items.find(item => item.convId === summary.convId)
      if (old && old.version > summary.version) return
      if (old) Object.assign(old, summary)
      else this.items.unshift(summary)
    },
    async load(more = false) {
      if (this.loading || (more && !this.hasMore)) return
      const generation = this.generation
      this.loading = true; this.error = ''
      try {
        const page = await listConvs(more ? this.current + 1 : 1)
        if (generation !== this.generation) return
        const existing = new Map(this.items.map(item => [item.convId, item]))
        const rows = page.records.map(row => {
          const current = existing.get(row.convId)
          return current && current.version > row.version ? current : row
        })
        const ids = new Set(rows.map(row => row.convId))
        const others = this.items.filter(item => !ids.has(item.convId))
        this.items = more ? [...others, ...rows] : rows
        this.current = page.current
        this.hasMore = page.current * page.size < page.total
      } catch (error) { if (generation === this.generation) this.error = errorMessage(error) }
      finally { if (generation === this.generation) this.loading = false }
    },
    async rename(convId: string, title: string, version: number) {
      const generation = this.generation
      try {
        const summary = await renameConv(convId, title, version)
        if (generation === this.generation) this.upsert(summary)
      } catch (error) {
        if (error instanceof ApiError && error.code === 409 && generation === this.generation) {
          const latest = await readConv(convId).catch(() => undefined)
          if (latest && generation === this.generation) this.upsert(latest)
        }
        throw error
      }
    },
    async remove(convId: string) {
      const generation = this.generation
      await deleteConv(convId)
      if (generation === this.generation) {
        this.items = this.items.filter(item => item.convId !== convId)
      }
    },
    async archive(convId: string) {
      const generation = this.generation
      await archiveConv(convId, true)
      if (generation === this.generation) {
        this.items = this.items.filter(item => item.convId !== convId)
      }
    },
    reset() { this.generation++; this.items = []; this.current = 0; this.hasMore = false; this.loading = false; this.error = '' },
  },
})
