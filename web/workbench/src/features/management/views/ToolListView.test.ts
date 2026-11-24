// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import ToolListView from './ToolListView.vue'
import { listTools } from '../api/agentConfigApi'
import type { Tool } from '../model/agentConfig'

const access = vi.hoisted(() => ({ codes: [] as string[] }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ path: '/management/tools', meta: { menuPath: '/management/tools' }, query: {} }),
}))
vi.mock('../stores/management', () => ({
  useManagementStore: () => ({ menus: [], can: (code: string) => access.codes.includes(code) }),
}))
vi.mock('../../auth/stores/auth', () => ({ useAuthStore: () => ({ user: { effectiveDisplayName: '管理员' } }) }))
vi.mock('../components/confirmManagement', () => ({ confirmManagement: vi.fn(() => Promise.resolve(true)) }))
vi.mock('../api/agentConfigApi', () => ({
  listTools: vi.fn(),
  enableTool: vi.fn(),
  disableTool: vi.fn(),
  deleteTool: vi.fn(),
}))

const RouterLink = {
  props: ['to', 'custom'],
  template: '<slot v-if="custom" :navigate="() => {}" /><a v-else :href="String(to)"><slot /></a>',
}

function tool(overrides: Partial<Tool>): Tool {
  return {
    toolId: '1',
    toolCode: 'TOOL-H-20261124-001',
    toolName: '订单查询',
    displayAlias: '查订单',
    toolType: 'HTTP',
    status: 'ENABLED',
    revision: 3,
    latestVersionId: '11',
    latestVersionNo: 2,
    latestReadiness: 'READY',
    exposedName: 'query_order',
    description: '查询订单',
    readOnly: true,
    usageCount: 1,
    lastUpdateTime: '2026-11-24T08:00:00Z',
    ...overrides,
  }
}

function mountView() {
  return mount(ToolListView, { global: { stubs: { RouterLink } } })
}

beforeEach(() => {
  vi.clearAllMocks()
  access.codes = ['management:tool:query', 'management:tool:edit', 'management:tool:publish']
})

it('renders tool gallery cards and filters by type pill', async () => {
  vi.mocked(listTools).mockResolvedValue([
    tool({}),
    tool({ toolId: '2', toolCode: 'TOOL-B-20261124-001', toolName: '当前时间', displayAlias: '', toolType: 'BUILTIN', exposedName: 'current_time' }),
  ])
  const view = mountView()
  await flushPromises()
  expect(view.findAll('.studio-card')).toHaveLength(2)

  const builtinPill = view.findAll('.studio-pill').find(p => p.text().includes('内置工具'))!
  await builtinPill.trigger('click')
  const cards = view.findAll('.studio-card')
  expect(cards).toHaveLength(1)
  expect(cards[0]!.text()).toContain('当前时间')
  expect(cards[0]!.text()).toContain('TOOL-B-20261124-001')
  view.unmount()
})

it('hides write actions without edit or publish permission', async () => {
  access.codes = ['management:tool:query']
  vi.mocked(listTools).mockResolvedValue([tool({})])
  const view = mountView()
  await flushPromises()

  expect(view.text()).not.toContain('新增工具')
  expect(view.find('.studio-more-btn').exists()).toBe(false)
  expect(view.text()).toContain('查看 →')
  view.unmount()
})
