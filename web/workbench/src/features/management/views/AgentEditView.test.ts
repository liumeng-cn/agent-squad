// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import AgentEditView from './AgentEditView.vue'
import {
  createAgentVersion,
  getAgent,
  getAgentVersion,
  listAgentToolOptions,
  listSubagentOptions,
  publishAgentVersion,
  saveAgentVersion,
} from '../api/agentConfigApi'
import type { AgentDetail, AgentVersion, ToolOption } from '../model/agentConfig'

const route = vi.hoisted(() => ({ value: null as unknown as { params: Record<string, string>; query: Record<string, string>; path: string; meta: Record<string, string> } }))

vi.mock('vue-router', () => ({
  useRoute: () => route.value,
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  onBeforeRouteLeave: vi.fn(),
  onBeforeRouteUpdate: vi.fn(),
}))
vi.mock('../stores/management', () => ({ useManagementStore: () => ({ menus: [], can: () => true }) }))
vi.mock('../../auth/stores/auth', () => ({ useAuthStore: () => ({ user: { effectiveDisplayName: '管理员' } }) }))
vi.mock('../components/confirmManagement', () => ({ confirmManagement: vi.fn(() => Promise.resolve(true)) }))
vi.mock('../api/agentConfigApi', () => ({
  getAgent: vi.fn(),
  getAgentVersion: vi.fn(),
  listAgentToolOptions: vi.fn(),
  listSubagentOptions: vi.fn(),
  saveAgentVersion: vi.fn(),
  createAgentVersion: vi.fn(),
  publishAgentVersion: vi.fn(),
  activateAgentVersion: vi.fn(),
  createAgent: vi.fn(),
  updateAgent: vi.fn(),
  enableAgent: vi.fn(),
  disableAgent: vi.fn(),
}))

const RouterLink = { props: ['to'], template: '<a :href="String(to)"><slot /></a>' }

const toolOptions: ToolOption[] = [
  { toolVersionId: '201', toolId: '20', toolCode: 'order_query', toolName: '订单查询', toolType: 'HTTP', exposedName: 'query_order', description: '查询订单', versionNo: 1, latest: false },
  { toolVersionId: '202', toolId: '20', toolCode: 'order_query', toolName: '订单查询', toolType: 'HTTP', exposedName: 'query_order', description: '查询订单', versionNo: 2, latest: true },
]

function draftVersion(overrides: Partial<AgentVersion> = {}): AgentVersion {
  return {
    agentVersionId: '101',
    agentId: '10',
    versionNo: 1,
    status: 'DRAFT',
    revision: 4,
    publishedAt: null,
    lastUpdateTime: '2026-11-21T08:00:00Z',
    description: '订单相关问题',
    systemPrompt: '你是订单助手。',
    maxIters: null,
    tools: [{
      toolVersionId: '201', toolId: '20', toolCode: 'order_query', toolName: '订单查询', toolType: 'HTTP',
      exposedName: 'query_order', description: '查询订单', versionNo: 1, latestVersionNo: 2, usable: true,
    }],
    subagents: [],
    ...overrides,
  }
}

function detail(): AgentDetail {
  return {
    agent: {
      agentId: '10', agentCode: 'order_assistant', agentName: '订单助手', agentType: 'MASTER', status: 'ENABLED', revision: 7,
      description: '订单相关问题', currentVersionId: null, currentVersionNo: null, draftVersionId: '101',
      toolCount: 1, subagentCount: 0, usageCount: 0, lastUpdateTime: '2026-11-21T08:00:00Z',
    },
    versions: [{ versionId: '101', versionNo: 1, status: 'DRAFT', revision: 4, publishedAt: null, lastUpdateTime: '2026-11-21T08:00:00Z', current: false }],
    usages: [],
  }
}

function mountView() {
  return mount(AgentEditView, { global: { stubs: { RouterLink } }, attachTo: document.body })
}

function button(view: ReturnType<typeof mountView>, text: string) {
  return view.findAll('button').find(item => item.text() === text)!
}

beforeEach(() => {
  vi.clearAllMocks()
  route.value = reactive({ params: { id: '10' }, query: {}, path: '/management/agents/10', meta: { menuPath: '/management/agents' } })
  vi.mocked(getAgent).mockResolvedValue(detail())
  vi.mocked(getAgentVersion).mockResolvedValue(draftVersion())
  vi.mocked(listAgentToolOptions).mockResolvedValue(toolOptions)
  vi.mocked(listSubagentOptions).mockResolvedValue([])
})

it('opens the draft, suggests upgrading a bound tool and saves the upgraded version with the draft revision', async () => {
  vi.mocked(saveAgentVersion).mockImplementation(async (_agentId, _versionId, body) => draftVersion({
    revision: 5,
    tools: [{ ...draftVersion().tools[0]!, toolVersionId: body.toolVersionIds[0]!, versionNo: 2 }],
  }))
  const view = mountView()
  await flushPromises()

  expect(getAgentVersion).toHaveBeenCalledWith('10', '101')
  expect(view.text()).toContain('可升级到 v2')

  await button(view, '升级').trigger('click')
  await button(view, '保存草稿').trigger('click')
  await flushPromises()

  expect(createAgentVersion).not.toHaveBeenCalled()
  expect(saveAgentVersion).toHaveBeenCalledWith('10', '101', expect.objectContaining({
    revision: 4,
    toolVersionIds: ['202'],
    systemPrompt: '你是订单助手。',
  }))
  expect(view.text()).toContain('草稿已保存')
  view.unmount()
})

it('requires saving before publishing and publishes with the version revision', async () => {
  vi.mocked(publishAgentVersion).mockResolvedValue(detail().agent)
  const view = mountView()
  await flushPromises()

  const publish = () => button(view, '发布并生效')
  expect(publish().attributes('disabled')).toBeUndefined()

  await view.find('textarea.config-prompt').setValue('你是订单助手，回答前先确认订单号。')
  expect(publish().attributes('disabled')).toBeDefined()
  expect(view.text()).toContain('有未保存的修改')
  view.unmount()
})

it('blocks publishing when the system prompt is empty', async () => {
  vi.mocked(getAgentVersion).mockResolvedValue(draftVersion({ systemPrompt: '' }))
  const view = mountView()
  await flushPromises()

  expect(button(view, '发布并生效').attributes('disabled')).toBeDefined()
  expect(button(view, '保存草稿').attributes('disabled')).toBeUndefined()
  expect(view.find('.config-check.is-fail').text()).toContain('系统提示词已填写')
  view.unmount()
})
