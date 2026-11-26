// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import AgentEditView from './AgentEditView.vue'
import {
  getAgent,
  listAgentToolOptions,
  listSubagentOptions,
  saveAgent,
} from '../api/agentConfigApi'
import type { AgentDetail, ToolOption } from '../model/agentConfig'

const route = vi.hoisted(() => ({
  value: null as unknown as {
    params: Record<string, string>
    query: Record<string, string>
    path: string
    meta: Record<string, string>
  },
}))

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
  getAgentRelease: vi.fn(),
  getAgentReleaseSubagent: vi.fn(),
  getAgentReleaseTool: vi.fn(),
  listAgentToolOptions: vi.fn(),
  listSubagentOptions: vi.fn(),
  createAgent: vi.fn(),
  saveAgent: vi.fn(),
}))

const RouterLink = { props: ['to'], template: '<a :href="String(to)"><slot /></a>' }

const toolOptions: ToolOption[] = [
  {
    toolId: '20',
    toolCode: 'TOOL-H-20261124-001',
    toolName: '订单查询',
    displayAlias: '查订单',
    toolType: 'HTTP',
    latestVersionId: '202',
    latestVersionNo: 2,
    exposedName: 'query_order',
    description: '查询订单',
    readOnly: true,
  },
]

function detail(overrides: Partial<AgentDetail> = {}): AgentDetail {
  return {
    agent: {
      agentId: '10',
      agentCode: 'AGT-M-20261124-001',
      agentName: '订单助手',
      displayAlias: '订单专员',
      agentType: 'MASTER',
      status: 'ENABLED',
      revision: 7,
      latestVersionId: '101',
      latestVersionNo: 1,
      latestReadiness: 'READY',
      description: '订单相关问题',
      currentReleaseId: null,
      currentReleaseNo: null,
      currentReleaseAt: null,
      publishState: 'UNPUBLISHED',
      toolCount: 1,
      subagentCount: 0,
      usageCount: 0,
      lastUpdateTime: '2026-11-24T08:00:00Z',
    },
    latestVersion: {
      agentVersionId: '101',
      agentId: '10',
      versionNo: 1,
      readiness: 'READY',
      description: '订单相关问题',
      promptSections: {
        role: '你是订单助手',
        goal: '解答订单状态',
        workflow: '',
        constraints: '',
        outputFormat: '',
      },
      systemPrompt: '## 角色与定位\n你是订单助手\n\n## 核心目标\n解答订单状态',
      maxIters: null,
      lastUpdateTime: '2026-11-24T08:00:00Z',
      tools: [
        {
          toolId: '20',
          toolCode: 'TOOL-H-20261124-001',
          toolName: '订单查询',
          displayAlias: '查订单',
          toolType: 'HTTP',
          toolStatus: 'ENABLED',
          toolVersionId: '202',
          versionNo: 2,
          readiness: 'READY',
          exposedName: 'query_order',
          description: '查询订单',
          readOnly: true,
          mcpServerId: null,
          mcpServerName: null,
          usable: true,
          unusableReason: null,
        },
      ],
      subagents: [],
    },
    versions: [{ versionId: '101', versionNo: 1, readiness: 'READY', lastUpdateTime: '2026-11-24T08:00:00Z' }],
    releases: [],
    usages: [],
    ...overrides,
  }
}

function mountView() {
  return mount(AgentEditView, { global: { stubs: { RouterLink } }, attachTo: document.body })
}

function button(view: ReturnType<typeof mountView>, text: string) {
  return view.findAll('button').find(item => item.text().trim() === text)!
}

beforeEach(() => {
  vi.clearAllMocks()
  route.value = reactive({ params: { id: '10' }, query: {}, path: '/management/agents/10', meta: { menuPath: '/management/agents' } })
  vi.mocked(getAgent).mockResolvedValue(detail())
  vi.mocked(listAgentToolOptions).mockResolvedValue(toolOptions)
  vi.mocked(listSubagentOptions).mockResolvedValue([])
})

it('renders sectioned editor with uppercase dated code and saves a new config version', async () => {
  const saved = detail()
  saved.agent.latestVersionNo = 2
  saved.agent.revision = 8
  vi.mocked(saveAgent).mockResolvedValue(saved)

  const view = mountView()
  await flushPromises()

  expect(view.text()).toContain('AGT-M-20261124-001')
  expect(view.text()).toContain('基础信息')
  expect(view.text()).toContain('结构化系统提示词')
  expect(view.text()).toContain('配置就绪度')

  await button(view, '保存').trigger('click')
  await flushPromises()

  expect(saveAgent).toHaveBeenCalledWith(
    '10',
    expect.objectContaining({
      revision: 7,
      agentName: '订单助手',
      displayAlias: '订单专员',
      toolIds: ['20'],
    }),
  )
  expect(view.text()).toContain('已保存为新配置版本 v2')
  view.unmount()
})

it.each(['query_order', 'ask_user'])('blocks saving when delegation name %s conflicts with tool or reserved name', async (delegationName) => {
  const d = detail()
  d.latestVersion!.subagents = [
    {
      subagentId: '30',
      subagentCode: 'AGT-S-20261124-001',
      subagentName: '订单子助手',
      subagentDisplayAlias: '订单专员',
      subagentStatus: 'ENABLED',
      subagentVersionId: '301',
      versionNo: 1,
      readiness: 'READY',
      delegationName,
      delegationDescription: '',
      displayAlias: '',
      subagentDescription: '订单查询',
      toolCount: 1,
      usable: true,
      unusableReason: null,
    },
  ]
  vi.mocked(getAgent).mockResolvedValue(d)

  const view = mountView()
  await flushPromises()
  await button(view, '保存').trigger('click')
  await flushPromises()

  expect(saveAgent).not.toHaveBeenCalled()
  expect(view.text()).toContain('Sub 委派')
  view.unmount()
})
