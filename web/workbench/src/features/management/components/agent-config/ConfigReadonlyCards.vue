<script setup lang="ts">
import { ElTag } from 'element-plus'
import { computed, ref } from 'vue'
import type {
  Agent,
  AgentVersion,
  McpServer,
  McpServerVersion,
  PromptSections,
  Tool,
  ToolVersion,
} from '../../model/agentConfig'
import {
  agentTypeLabels,
  configStatusLabels,
  configStatusTones,
  formatTime,
  mcpTransportLabels,
  publishStateLabels,
  publishStateTones,
  readinessLabels,
  readinessTones,
  toolTypeLabels,
  versionLabel,
} from '../../model/agentConfigLabels'
import { tagType } from '../../model/elementTheme'
import { fieldsFromSchema } from '../../model/toolSchema'

/**
 * 结构化只读详情卡片：替代灰掉的 disabled 表单，为无编辑权限查看、只读预览与历史配置版本提供清晰阅读视图。
 */
const props = defineProps<{
  mcp?: { server?: McpServer | null; version: McpServerVersion }
  tool?: { summary?: Tool | null; version: ToolVersion }
  agent?: { summary?: Agent | null; version: AgentVersion; agentType?: 'MASTER' | 'SUB_AGENT' }
}>()

const showRawPrompt = ref(false)
const showRawSchema = ref(false)

const toolFields = computed(() =>
  props.tool ? fieldsFromSchema(props.tool.version.inputSchema ?? { type: 'object', properties: {} }) : [],
)

const formattedToolSchema = computed(() =>
  props.tool ? JSON.stringify(props.tool.version.inputSchema ?? {}, null, 2) : '',
)

const resolvedAgentType = computed(() => props.agent?.summary?.agentType ?? props.agent?.agentType ?? 'MASTER')

const isStandaloneMaster = computed(() => {
  if (!props.agent || resolvedAgentType.value !== 'MASTER') return false
  return (props.agent.version.tools?.length ?? 0) === 0 && (props.agent.version.subagents?.length ?? 0) === 0
})

const promptItems = computed(() => {
  const s: PromptSections | undefined = props.agent?.version.promptSections
  if (!s) return []
  return [
    { key: 'role', title: '角色与定位', content: s.role, required: true },
    { key: 'goal', title: '核心目标', content: s.goal, required: true },
    { key: 'workflow', title: '工作流程与策略', content: s.workflow, required: false },
    { key: 'constraints', title: '约束与边界', content: s.constraints, required: false },
    { key: 'outputFormat', title: '输出规范', content: s.outputFormat, required: false },
  ]
})
</script>

<template>
  <!-- MCP 只读详情 -->
  <div v-if="mcp" class="ro-sheet">
    <section class="ro-section">
      <div class="ro-meta-grid">
        <div v-if="mcp.server" class="ro-meta-item">
          <span class="ro-meta-label">服务名称</span>
          <span class="ro-meta-value is-strong">{{ mcp.server.serverName }}</span>
        </div>
        <div v-if="mcp.server" class="ro-meta-item">
          <span class="ro-meta-label">组件编码</span>
          <span class="ro-meta-value management-code">{{ mcp.server.serverCode }}</span>
        </div>
        <div class="ro-meta-item">
          <span class="ro-meta-label">配置版本</span>
          <span class="ro-meta-value management-tags">
            <ElTag size="small" type="info">{{ versionLabel(mcp.version.versionNo) }}</ElTag>
            <ElTag
              size="small"
              :type="tagType(readinessTones[mcp.version.readiness])"
            >
              {{ readinessLabels[mcp.version.readiness] }}
            </ElTag>
            <ElTag
              v-if="mcp.server"
              size="small"
              :type="tagType(configStatusTones[mcp.server.status])"
            >
              {{ configStatusLabels[mcp.server.status] }}
            </ElTag>
          </span>
        </div>
        <div class="ro-meta-item">
          <span class="ro-meta-label">传输方式</span>
          <span class="ro-meta-value">
            {{ mcp.version.transportType ? mcpTransportLabels[mcp.version.transportType] : '未设置' }}
          </span>
        </div>
        <div class="ro-meta-item">
          <span class="ro-meta-label">凭据引用</span>
          <span class="ro-meta-value">{{ mcp.version.credentialRef || '不使用凭据' }}</span>
        </div>
        <div class="ro-meta-item">
          <span class="ro-meta-label">连接超时 / 保存时间</span>
          <span class="ro-meta-value">{{ mcp.version.timeoutMs }} ms · {{ formatTime(mcp.version.lastUpdateTime) }}</span>
        </div>
      </div>

      <div v-if="mcp.server?.description" class="ro-prose-box">
        <div class="ro-sub-label">服务说明</div>
        <p class="ro-prose-text">{{ mcp.server.description }}</p>
      </div>
    </section>

    <section class="ro-section">
      <h3 class="ro-section-title">连接端点与请求头</h3>
      <div class="ro-endpoint-bar">
        <span class="ro-method-badge is-mcp">{{ mcp.version.transportType || 'MCP' }}</span>
        <code class="ro-endpoint-url">{{ mcp.version.endpoint || '尚未配置连接地址' }}</code>
      </div>

      <div v-if="mcp.version.headers?.length" class="ro-kv-table">
        <div v-for="(h, idx) in mcp.version.headers" :key="idx" class="ro-kv-row">
          <span class="ro-kv-key">{{ h.name }}</span>
          <span class="ro-kv-val">{{ h.value }}</span>
        </div>
      </div>
      <div v-else class="ro-subtle-empty">未配置附加请求头</div>
    </section>
  </div>

  <!-- Tool 只读详情 -->
  <div v-else-if="tool" class="ro-sheet">
    <section class="ro-section">
      <div class="ro-meta-grid">
        <div v-if="tool.summary" class="ro-meta-item">
          <span class="ro-meta-label">工具名称 / 展示别称</span>
          <span class="ro-meta-value is-strong">
            {{ tool.summary.toolName }}
            <span v-if="tool.summary.displayAlias" class="management-muted">（别称：{{ tool.summary.displayAlias }}）</span>
          </span>
        </div>
        <div v-if="tool.summary" class="ro-meta-item">
          <span class="ro-meta-label">组件编码</span>
          <span class="ro-meta-value management-code">{{ tool.summary.toolCode }}</span>
        </div>
        <div class="ro-meta-item">
          <span class="ro-meta-label">类型与状态</span>
          <span class="ro-meta-value management-tags">
            <ElTag size="small" type="info">{{ toolTypeLabels[tool.version.toolType] }}</ElTag>
            <ElTag size="small" type="info">{{ versionLabel(tool.version.versionNo) }}</ElTag>
            <ElTag size="small" :type="tagType(readinessTones[tool.version.readiness])">
              {{ readinessLabels[tool.version.readiness] }}
            </ElTag>
          </span>
        </div>
        <div class="ro-meta-item">
          <span class="ro-meta-label">模型工具名（exposedName）</span>
          <span class="ro-meta-value management-code">{{ tool.version.exposedName || '未命名' }}</span>
        </div>
        <div class="ro-meta-item">
          <span class="ro-meta-label">读写属性</span>
          <span class="ro-meta-value">{{ tool.version.readOnly ? '只读工具' : '包含写操作' }}</span>
        </div>
        <div class="ro-meta-item">
          <span class="ro-meta-label">保存时间</span>
          <span class="ro-meta-value">{{ formatTime(tool.version.lastUpdateTime) }}</span>
        </div>
      </div>

      <div class="ro-prose-box">
        <div class="ro-sub-label">模型调用说明</div>
        <p class="ro-prose-text">{{ tool.version.description || '尚未填写调用说明' }}</p>
      </div>
    </section>

    <section class="ro-section">
      <h3 class="ro-section-title">底层调用实现（{{ toolTypeLabels[tool.version.toolType] }}）</h3>

      <template v-if="tool.version.toolType === 'BUILTIN'">
        <div class="ro-meta-grid">
          <div class="ro-meta-item">
            <span class="ro-meta-label">内置实现标识</span>
            <span class="ro-meta-value management-code">{{ tool.version.implementationKey || '未选择' }}</span>
          </div>
        </div>
      </template>

      <template v-else-if="tool.version.toolType === 'MCP'">
        <div class="ro-meta-grid">
          <div class="ro-meta-item">
            <span class="ro-meta-label">关联 MCP 服务</span>
            <span class="ro-meta-value is-strong">{{ tool.version.mcpServerName || '未选择' }}</span>
          </div>
          <div class="ro-meta-item">
            <span class="ro-meta-label">远程工具名</span>
            <span class="ro-meta-value management-code">{{ tool.version.remoteToolName || '未选择' }}</span>
          </div>
        </div>
      </template>

      <template v-else-if="tool.version.httpSpec">
        <div class="ro-endpoint-bar">
          <span class="ro-method-badge">{{ tool.version.httpSpec.method }}</span>
          <code class="ro-endpoint-url">{{ tool.version.httpSpec.url || '尚未配置请求地址' }}</code>
        </div>
        <div class="ro-meta-grid" style="margin-top: 10px">
          <div class="ro-meta-item">
            <span class="ro-meta-label">凭据引用</span>
            <span class="ro-meta-value">{{ tool.version.httpSpec.credentialRef || '不使用凭据' }}</span>
          </div>
          <div class="ro-meta-item">
            <span class="ro-meta-label">超时 / 结果提取路径</span>
            <span class="ro-meta-value">
              {{ tool.version.httpSpec.timeoutMs }} ms · {{ tool.version.httpSpec.resultPointer || '完整响应体' }}
            </span>
          </div>
        </div>
        <div v-if="tool.version.httpSpec.headers?.length" class="ro-kv-table">
          <div v-for="(h, idx) in tool.version.httpSpec.headers" :key="idx" class="ro-kv-row">
            <span class="ro-kv-key">{{ h.name }}</span>
            <span class="ro-kv-val">{{ h.value }}</span>
          </div>
        </div>
        <div v-if="tool.version.httpSpec.bodyTemplate" class="ro-prose-box">
          <div class="ro-sub-label">请求体模板</div>
          <pre class="prompt-preview-content">{{ tool.version.httpSpec.bodyTemplate }}</pre>
        </div>
      </template>
    </section>

    <section class="ro-section">
      <div class="studio-subblock-head">
        <h3 class="ro-section-title">输入参数契约（{{ toolFields.length }}）</h3>
        <button type="button" class="management-link-btn" @click="showRawSchema = !showRawSchema">
          {{ showRawSchema ? '收起 JSON Schema' : '查看 JSON Schema' }}
        </button>
      </div>

      <div v-if="toolFields.length" class="ro-param-list">
        <div v-for="field in toolFields" :key="field.name" class="ro-param-row">
          <span class="management-code">{{ field.name }}</span>
          <ElTag size="small" type="info">{{ field.type }}</ElTag>
          <ElTag v-if="field.required" size="small" type="danger">必填</ElTag>
          <span class="ro-param-desc">{{ field.description || '无说明' }}</span>
        </div>
      </div>
      <div v-else class="ro-subtle-empty">无输入参数（空对象契约）</div>

      <pre v-if="showRawSchema" class="prompt-preview-content" style="margin-top: 8px">{{ formattedToolSchema }}</pre>
    </section>
  </div>

  <!-- Agent 只读详情 -->
  <div v-else-if="agent" class="ro-sheet">
    <section class="ro-section">
      <div class="ro-meta-grid">
        <div v-if="agent.summary" class="ro-meta-item">
          <span class="ro-meta-label">Agent 名称 / 展示别称</span>
          <span class="ro-meta-value is-strong">
            {{ agent.summary.agentName }}
            <span v-if="agent.summary.displayAlias" class="management-muted">（别称：{{ agent.summary.displayAlias }}）</span>
          </span>
        </div>
        <div v-if="agent.summary" class="ro-meta-item">
          <span class="ro-meta-label">组件编码</span>
          <span class="ro-meta-value management-code">{{ agent.summary.agentCode }}</span>
        </div>
        <div class="ro-meta-item">
          <span class="ro-meta-label">类型与版本状态</span>
          <span class="ro-meta-value management-tags">
            <ElTag size="small" :type="resolvedAgentType === 'MASTER' ? 'primary' : 'info'">
              {{ agentTypeLabels[resolvedAgentType] }}
            </ElTag>
            <ElTag size="small" type="info">{{ versionLabel(agent.version.versionNo) }}</ElTag>
            <ElTag size="small" :type="tagType(readinessTones[agent.version.readiness])">
              {{ readinessLabels[agent.version.readiness] }}
            </ElTag>
            <ElTag
              v-if="agent.summary?.publishState && resolvedAgentType === 'MASTER'"
              size="small"
              :type="tagType(publishStateTones[agent.summary.publishState])"
            >
              {{ publishStateLabels[agent.summary.publishState] }}
              <template v-if="agent.summary.currentReleaseNo != null"> #{{ agent.summary.currentReleaseNo }}</template>
            </ElTag>
          </span>
        </div>
        <div class="ro-meta-item">
          <span class="ro-meta-label">运行模式与步数上限</span>
          <span class="ro-meta-value">
            <template v-if="resolvedAgentType === 'MASTER'">
              {{ isStandaloneMaster ? '独立对话模式（无外部组件）' : `协同编排（${agent.version.tools.length} 工具 · ${agent.version.subagents.length} Sub）` }}
              ·
            </template>
            {{ agent.version.maxIters ? `最多 ${agent.version.maxIters} 步` : '平台默认步数' }}
          </span>
        </div>
      </div>

      <div v-if="agent.version.description" class="ro-prose-box">
        <div class="ro-sub-label">职责描述</div>
        <p class="ro-prose-text">{{ agent.version.description }}</p>
      </div>
    </section>

    <section class="ro-section">
      <div class="studio-subblock-head">
        <h3 class="ro-section-title">结构化系统提示词</h3>
        <button type="button" class="management-link-btn" @click="showRawPrompt = !showRawPrompt">
          {{ showRawPrompt ? '收起完整拼装 Markdown' : '查看完整拼装 Markdown' }}
        </button>
      </div>

      <div class="ro-prompt-grid">
        <div
          v-for="sec in promptItems"
          :key="sec.key"
          class="ro-prompt-block"
          :class="{ 'is-empty': !sec.content?.trim() }"
        >
          <div class="ro-prompt-head">
            <span>{{ sec.title }}</span>
            <span v-if="sec.required && !sec.content?.trim()" class="ro-missing-badge">待补充</span>
          </div>
          <p class="ro-prompt-body">{{ sec.content?.trim() || '未填写' }}</p>
        </div>
      </div>

      <pre v-if="showRawPrompt" class="prompt-preview-content" style="margin-top: 10px">{{ agent.version.systemPrompt || '（空）' }}</pre>
    </section>

    <section class="ro-section">
      <h3 class="ro-section-title">挂载组件拓扑</h3>

      <div v-if="isStandaloneMaster" class="ro-standalone-banner">
        <strong>独立对话模式</strong>
        <span>该版本未挂载任何外部工具或子 Agent，发布后将直接基于系统提示词与平台基础对话协议独立运行。</span>
      </div>

      <template v-else>
        <div class="ro-sub-label">直接工具（{{ agent.version.tools?.length ?? 0 }}）</div>
        <div v-if="agent.version.tools?.length" class="ro-chip-list">
          <div v-for="t in agent.version.tools" :key="t.toolId" class="ro-chip-card">
            <div class="ro-chip-main">
              <span class="is-strong">{{ t.toolName }}</span>
              <span v-if="t.displayAlias" class="management-muted">· {{ t.displayAlias }}</span>
              <span class="management-code">{{ t.exposedName }}</span>
            </div>
            <span class="management-tags">
              <ElTag size="small" type="info">{{ toolTypeLabels[t.toolType] }}</ElTag>
              <ElTag size="small" type="info">{{ versionLabel(t.versionNo) }}</ElTag>
            </span>
          </div>
        </div>
        <div v-else class="ro-subtle-empty">未绑定直接工具</div>

        <template v-if="resolvedAgentType === 'MASTER'">
          <div class="ro-sub-label" style="margin-top: 10px">Sub 委派（{{ agent.version.subagents?.length ?? 0 }}）</div>
          <div v-if="agent.version.subagents?.length" class="ro-chip-list">
            <div v-for="s in agent.version.subagents" :key="s.subagentId" class="ro-chip-card">
              <div class="ro-chip-main">
                <span class="is-strong">{{ s.subagentName }}</span>
                <span v-if="s.displayAlias" class="management-muted">· 展示名 {{ s.displayAlias }}</span>
                <span class="management-code">委派名 {{ s.delegationName }}</span>
              </div>
              <span class="management-tags">
                <ElTag size="small" type="info">{{ versionLabel(s.versionNo) }}</ElTag>
                <ElTag size="small" type="info">{{ s.toolCount }} 工具</ElTag>
              </span>
            </div>
          </div>
          <div v-else class="ro-subtle-empty">未配置子 Agent 委派</div>
        </template>
      </template>
    </section>
  </div>
</template>
