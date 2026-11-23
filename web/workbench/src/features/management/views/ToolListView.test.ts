// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import ToolListView from './ToolListView.vue'
import { disableTool, listTools } from '../api/agentConfigApi'
import type { Tool } from '../model/agentConfig'

const access = vi.hoisted(() => ({ codes: [] as string[] }))
const confirm = vi.hoisted(() => ({ answer: true }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ path: '/management/tools', meta: { menuPath: '/management/tools' }, query: {} }),
}))
vi.mock('../stores/management', () => ({
  useManagementStore: () => ({ menus: [], can: (code: string) => access.codes.includes(code) }),
}))
vi.mock('../../auth/stores/auth', () => ({ useAuthStore: () => ({ user: { effectiveDisplayName: '管理员' } }) }))
vi.mock('../components/confirmManagement', () => ({ confirmManagement: vi.fn(() => Promise.resolve(confirm.answer)) }))
vi.mock('../api/agentConfigApi', () => ({
  listTools: vi.fn(),
  listMcpServers: vi.fn(() => Promise.resolve([])),
  listAgents: vi.fn(() => Promise.resolve([])),
  enableTool: vi.fn(),
  disableTool: vi.fn(),
}))

const RouterLink = {
  props: ['to', 'custom'],
  template: '<slot v-if="custom" :navigate="() => {}" /><a v-else :href="String(to)"><slot /></a>',
}

function tool(overrides: Partial<Tool>): Tool {
  return {
    toolId: '1',
    toolCode: 'order_query',
    toolName: '订单查询',
    toolType: 'HTTP',
    status: 'ENABLED',
    revision: 3,
    exposedName: 'query_order',
    description: '查询订单',
    publishedVersionId: '11',
    publishedVersionNo: 2,
    draftVersionId: null,
    usageCount: 1,
    lastUpdateTime: '2026-11-21T08:00:00Z',
    ...overrides,
  }
}

function mountView() {
  return mount(ToolListView, { global: { stubs: { RouterLink } } })
}

beforeEach(() => {
  vi.clearAllMocks()
  confirm.answer = true
  access.codes = ['management:tool:query', 'management:tool:edit', 'management:tool:publish']
})

it('filters by type only after the query is submitted', async () => {
  vi.mocked(listTools).mockResolvedValue([
    tool({}),
    tool({ toolId: '2', toolCode: 'clock', toolName: '当前时间', toolType: 'BUILTIN', exposedName: 'current_time' }),
  ])
  const view = mountView()
  await flushPromises()
  expect(view.findAll('tbody tr')).toHaveLength(2)

  await view.findAll('select')[0]!.setValue('BUILTIN')
  expect(view.findAll('tbody tr')).toHaveLength(2)
  await view.find('form').trigger('submit')
  const rows = view.findAll('tbody tr')
  expect(rows).toHaveLength(1)
  expect(rows[0]!.text()).toContain('当前时间')
  view.unmount()
})

it('shows the dependency hint when the catalog is empty', async () => {
  vi.mocked(listTools).mockResolvedValue([])
  const view = mountView()
  await flushPromises()
  expect(view.text()).toContain('MCP 工具需要先发布 MCP 服务')
  view.unmount()
})

it('reports the backend reason when a referenced tool cannot be disabled', async () => {
  vi.mocked(listTools).mockResolvedValue([tool({})])
  vi.mocked(disableTool).mockRejectedValue(new Error('工具被 Agent“订单助手”的生效版本绑定，不能停用'))
  const view = mountView()
  await flushPromises()

  const disableButton = view.findAll('.management-actions button').find(button => button.text() === '停用')!
  await disableButton.trigger('click')
  await flushPromises()

  expect(disableTool).toHaveBeenCalledWith('1', 3)
  expect(view.find('[role=alert]').text()).toContain('订单助手')
  view.unmount()
})

it('hides write actions without edit or publish permission', async () => {
  access.codes = ['management:tool:query']
  vi.mocked(listTools).mockResolvedValue([tool({})])
  const view = mountView()
  await flushPromises()

  const actions = view.findAll('.management-actions button').map(button => button.text())
  expect(actions).toEqual(['查看'])
  expect(view.text()).not.toContain('新增工具')
  view.unmount()
})
