// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AgentObservationLink from './AgentObservationLink.vue'
import { useAuthStore } from '../../features/auth/stores/auth'

afterEach(() => vi.unstubAllEnvs())
describe('Agent observation entry', () => {
  function render(role: 'USER' | 'ADMIN') {
    const pinia = createPinia()
    setActivePinia(pinia)
    const auth = useAuthStore()
    auth.user = { roles: [role] } as typeof auth.user
    return mount(AgentObservationLink, { global: { plugins: [pinia] } })
  }
  it('opens the Langfuse UI in a new tab for administrators', () => {
    vi.stubEnv('VITE_OBSERVATION_URL', 'http://127.0.0.1:13000')
    const link = render('ADMIN').get('a')
    expect(link.attributes('href')).toBe('http://127.0.0.1:13000/')
    expect(link.attributes('target')).toBe('_blank')
    expect(link.attributes('rel')).toBe('noopener noreferrer')
  })
  it('does not show the administration entry to regular users', () => {
    expect(render('USER').find('a').exists()).toBe(false)
  })
  it('rejects executable URLs and credentials in URLs', () => {
    vi.stubEnv('VITE_OBSERVATION_URL', 'javascript:alert(1)')
    expect(render('ADMIN').find('a').exists()).toBe(false)
    vi.stubEnv('VITE_OBSERVATION_URL', 'http://user:password@localhost:13001')
    expect(render('ADMIN').find('a').exists()).toBe(false)
  })
})
