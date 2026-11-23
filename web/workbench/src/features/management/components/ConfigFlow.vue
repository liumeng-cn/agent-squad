<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useManagementStore } from '../stores/management'
import { listAgents, listMcpServers, listTools } from '../api/agentConfigApi'
import type { FlowStep } from '../model/agentConfigLabels'

/**
 * 配置流程条：按依赖顺序展示 MCP 服务 → 工具 → 子 Agent → 主 Agent 的已发布数量，点击跳转到对应列表。
 * 只读取当前用户有查询权限的目录；无权限的环节只展示名称，不发起请求。
 */
const props = defineProps<{ current: FlowStep | FlowStep[] }>()

interface StepCount { published: number; total: number }
interface FlowItem { key: FlowStep; title: string; path: string; optional?: boolean; allowed: boolean }

const access = useManagementStore()
const counts = ref<Partial<Record<FlowStep, StepCount>>>({})
const failed = ref(false)
const currentSteps = computed(() => (Array.isArray(props.current) ? props.current : [props.current]))

const steps = computed<FlowItem[]>(() => [
  { key: 'mcp', title: 'MCP 服务', path: '/management/mcp-servers', optional: true, allowed: access.can('management:mcp:query') },
  { key: 'tools', title: '工具', path: '/management/tools', allowed: access.can('management:tool:query') },
  { key: 'subagents', title: '子 Agent', path: '/management/agents?type=SUB_AGENT', optional: true, allowed: access.can('management:agent:query') },
  { key: 'masters', title: '主 Agent', path: '/management/agents?type=MASTER', allowed: access.can('management:agent:query') },
])

function countText(step: FlowItem): string {
  if (!step.allowed) {
    return '无查询权限'
  }
  const count = counts.value[step.key]
  if (!count) {
    return failed.value ? '数量暂不可用' : '统计中…'
  }
  return `已发布 ${count.published} / 共 ${count.total}`
}

/** 各目录独立加载，某一目录失败不影响其他环节展示。 */
async function load() {
  const tasks: Promise<void>[] = []
  if (access.can('management:mcp:query')) {
    tasks.push(listMcpServers().then(items => {
      counts.value.mcp = { published: items.filter(item => item.publishedVersionId).length, total: items.length }
    }))
  }
  if (access.can('management:tool:query')) {
    tasks.push(listTools().then(items => {
      counts.value.tools = { published: items.filter(item => item.publishedVersionId).length, total: items.length }
    }))
  }
  if (access.can('management:agent:query')) {
    tasks.push(listAgents().then(items => {
      const subagents = items.filter(item => item.agentType === 'SUB_AGENT')
      const masters = items.filter(item => item.agentType === 'MASTER')
      counts.value.subagents = { published: subagents.filter(item => item.currentVersionId).length, total: subagents.length }
      counts.value.masters = { published: masters.filter(item => item.currentVersionId).length, total: masters.length }
    }))
  }
  const results = await Promise.allSettled(tasks)
  failed.value = results.some(result => result.status === 'rejected')
}


onMounted(() => {
  void load()
})
</script>

<template>
  <section class="management-card config-flow" aria-label="配置流程">
    <div class="config-flow-head">
      <h2 class="config-flow-heading">配置流程</h2>
      <p class="config-flow-hint">按依赖顺序配置：下游只能引用上游已发布且启用的版本，被生效引用的上游不能停用。</p>
    </div>
    <ol class="config-flow-steps">
      <li
        v-for="(step, index) in steps"
        :key="step.key"
        class="config-flow-step"
        :class="{ 'is-current': currentSteps.includes(step.key) }"
      >
        <component
          :is="step.allowed ? 'RouterLink' : 'div'"
          class="config-flow-link"
          :to="step.allowed ? step.path : undefined"
          :aria-current="currentSteps.includes(step.key) ? 'step' : undefined"
        >
          <span class="config-flow-index">{{ index + 1 }}</span>
          <span class="config-flow-title">
            {{ step.title }}
            <span v-if="step.optional" class="config-flow-optional">可选</span>
          </span>
          <span class="config-flow-count">{{ countText(step) }}</span>
        </component>
      </li>
    </ol>
  </section>
</template>
