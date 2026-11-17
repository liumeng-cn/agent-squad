import { defineStore } from 'pinia'
import { listSessions, renameSession } from '../api/sessionApi'
import type { SessionSummary } from '../model/session'
import { errorMessage } from '../../../shared/http/http'
export const useSessionsStore = defineStore('chat-sessions', {
  state: () => ({ items: [] as SessionSummary[], nextCursor: null as string | null, hasMore: false, loading: false, error: '', generation: 0 }),
  actions: {
    upsert(summary: SessionSummary) {
      const old = this.items.find(item => item.sessionId === summary.sessionId)
      if (old && old.version > summary.version) return
      if (old) Object.assign(old, summary)
      else this.items.push(summary)
      this.items.sort((a, b) => b.lastActivityAt.localeCompare(a.lastActivityAt))
    },
    async load(more = false) {
      if (this.loading || (more && !this.nextCursor)) return
      const generation = this.generation
      this.loading = true; this.error = ''
      try {
        const page = await listSessions(more ? this.nextCursor ?? undefined : undefined)
        if (generation !== this.generation) return
        for (const row of page.items) this.upsert(row)
        if (!more) {
          const ids = new Set(page.items.map(item => item.sessionId))
          const current = new Map(this.items.map(item => [item.sessionId, item]))
          this.items = [...page.items.map(item => current.get(item.sessionId)!), ...this.items.filter(item => !ids.has(item.sessionId))]
          this.items.sort((a, b) => b.lastActivityAt.localeCompare(a.lastActivityAt))
        }
        if (more || this.items.length <= 30) { this.nextCursor = page.nextCursor; this.hasMore = page.hasMore }
      } catch (error) { if (generation === this.generation) this.error = errorMessage(error) }
      finally { if (generation === this.generation) this.loading = false }
    },
    async rename(sessionId: string, title: string, version: number) { this.upsert(await renameSession(sessionId, title, version)) },
    reset() { this.generation++; this.items = []; this.nextCursor = null; this.hasMore = false; this.loading = false; this.error = '' },
  },
})
