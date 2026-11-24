<script setup lang="ts">
import { ElButton, ElCollapse, ElCollapseItem, ElInput, ElTooltip } from 'element-plus'
import { computed, ref } from 'vue'
import type { PromptSections } from '../../model/agentConfig'
import { assemblePromptSections } from '../../model/agentConfigLabels'

/**
 * 五段式结构化提示词编辑器（业界标准 Role / Goal / Workflow / Constraints / OutputFormat），
 * 支持实时拼装预览，并为后续 AI 辅助填写与局部润色预留入口。
 */
const model = defineModel<PromptSections>({ required: true })
defineProps<{ readonly: boolean }>()

const showPreview = ref(false)
const optionalOpen = ref<string[]>(['workflow'])
const assembled = computed(() => assemblePromptSections(model.value))

function updateField(key: keyof PromptSections, value: string) {
  model.value = { ...model.value, [key]: value }
}
</script>

<template>
  <div class="prompt-sections-editor">
    <div class="prompt-sections-toolbar">
      <span class="management-help">
        按业界规范分模块编写提示词，保存时自动拼装为系统提示词；“角色与定位”与“核心目标”填写后才算就绪。
      </span>
      <div class="management-actions">
        <ElButton native-type="button" @click="showPreview = !showPreview">
          {{ showPreview ? '隐藏拼装预览' : '预览完整提示词' }}
        </ElButton>
        <ElTooltip content="后续版本将支持结合已绑定工具与 Sub 职责，由 AI 自动生成或润色各模块" placement="top">
          <ElButton native-type="button" disabled>AI 辅助填写（规划中）</ElButton>
        </ElTooltip>
      </div>
    </div>

    <div class="management-form-grid">
      <label class="management-field is-full">
        <span class="management-field-label">角色与定位（Role） <span class="management-required" /></span>
        <ElInput
          type="textarea"
          :rows="3"
          :disabled="readonly"
          :model-value="model.role"
          maxlength="4000"
          show-word-limit
          placeholder="例如：你是企业订单服务专家，负责帮助用户查询订单、定位物流异常并解答售后政策。"
          @update:model-value="updateField('role', String($event))"
        />
      </label>

      <label class="management-field is-full">
        <span class="management-field-label">核心目标（Goal） <span class="management-required" /></span>
        <ElInput
          type="textarea"
          :rows="3"
          :disabled="readonly"
          :model-value="model.goal"
          maxlength="4000"
          show-word-limit
          placeholder="例如：准确理解用户诉求，优先通过真实工具核查数据，在最少交互轮次内给出可执行的明确答复。"
          @update:model-value="updateField('goal', String($event))"
        />
      </label>
    </div>

    <ElCollapse v-model="optionalOpen" style="margin-top: 12px">
      <ElCollapseItem title="工作流程与工具/委派策略（Workflow，选填）" name="workflow">
        <ElInput
          type="textarea"
          :rows="4"
          :disabled="readonly"
          :model-value="model.workflow"
          maxlength="6000"
          show-word-limit
          placeholder="描述问题拆解步骤、何时调用具体工具、何时委派给对应 SubAgent，以及缺少关键参数时如何追问。"
          @update:model-value="updateField('workflow', String($event))"
        />
      </ElCollapseItem>
      <ElCollapseItem title="约束与边界（Constraints，选填）" name="constraints">
        <ElInput
          type="textarea"
          :rows="3"
          :disabled="readonly"
          :model-value="model.constraints"
          maxlength="4000"
          show-word-limit
          placeholder="例如：禁止编造订单号或接口返回；涉及敏感操作必须先征得用户确认；无法核实时明确说明原因。"
          @update:model-value="updateField('constraints', String($event))"
        />
      </ElCollapseItem>
      <ElCollapseItem title="输出规范（Output Format，选填）" name="outputFormat">
        <ElInput
          type="textarea"
          :rows="3"
          :disabled="readonly"
          :model-value="model.outputFormat"
          maxlength="2000"
          show-word-limit
          placeholder="例如：使用简洁中文回答，多条数据使用 Markdown 表格，关键结论置于开头。"
          @update:model-value="updateField('outputFormat', String($event))"
        />
      </ElCollapseItem>
    </ElCollapse>

    <div v-if="showPreview" class="prompt-preview-box">
      <div class="prompt-preview-title">完整系统提示词预览（运行时末尾自动追加平台交互约定）</div>
      <pre class="prompt-preview-content">{{ assembled || '尚未填写任何提示词模块' }}</pre>
    </div>
  </div>
</template>
