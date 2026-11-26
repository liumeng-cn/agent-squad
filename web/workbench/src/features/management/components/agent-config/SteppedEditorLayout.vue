<script setup lang="ts">
import { ElButton } from 'element-plus'

export interface EditorStepItem {
  key: string
  no: number
  label: string
  badge?: string | number
  hidden?: boolean
}

/**
 * 方案 3 共用分步聚焦编辑框架：左侧简洁步骤导航 + 右侧单一居中聚焦画布，告别多层卡片套娃。
 */
const activeStep = defineModel<string>('step', { required: true })
const readonlyView = defineModel<boolean>('readonlyView', { default: false })

defineProps<{
  backPath: string
  backLabel: string
  title: string
  code?: string
  subtitle?: string
  steps: EditorStepItem[]
  canSave?: boolean
  canToggleReadonly?: boolean
  saving?: boolean
  saveLabel?: string
}>()

const emit = defineEmits<{ save: [] }>()
</script>

<template>
  <div class="studio-step-shell">
    <!-- 左侧步骤导航栏 -->
    <aside class="studio-step-rail">
      <RouterLink class="studio-back-link" :to="backPath">← 返回{{ backLabel }}</RouterLink>

      <div class="studio-rail-head">
        <h1 class="studio-rail-title">{{ title }}</h1>
        <div v-if="code" class="studio-rail-code">{{ code }}</div>
        <div v-else class="studio-rail-sub">保存后自动生成规整编码</div>
        <div v-if="$slots.status" class="studio-rail-status">
          <slot name="status" />
        </div>
      </div>

      <nav class="studio-step-nav" aria-label="配置步骤">
        <button
          v-for="item in steps.filter(s => !s.hidden)"
          :key="item.key"
          type="button"
          class="studio-step-btn"
          :class="{ 'is-active': activeStep === item.key }"
          @click="activeStep = item.key"
        >
          <span class="studio-step-no">{{ item.no }}</span>
          <span class="studio-step-label">{{ item.label }}</span>
          <span v-if="item.badge !== undefined && item.badge !== ''" class="studio-step-badge">{{ item.badge }}</span>
        </button>
      </nav>

      <div v-if="subtitle" class="studio-rail-foot">{{ subtitle }}</div>
    </aside>

    <!-- 右侧单一居中聚焦画布 -->
    <section class="studio-step-canvas">
      <div class="studio-canvas-card">
        <header class="studio-canvas-head">
          <div>
            <h2 class="studio-canvas-title">
              <slot name="step-title" />
            </h2>
            <p v-if="$slots['step-desc']" class="studio-canvas-desc">
              <slot name="step-desc" />
            </p>
          </div>
          <div class="studio-canvas-actions">
            <slot name="extra-actions" />
            <ElButton
              v-if="canToggleReadonly && canSave"
              native-type="button"
              @click="readonlyView = !readonlyView"
            >
              {{ readonlyView ? '返回编辑' : '只读视图' }}
            </ElButton>
            <ElButton
              v-if="canSave && !readonlyView"
              type="primary"
              native-type="button"
              class="management-primary"
              :loading="saving"
              @click="emit('save')"
            >
              {{ saveLabel || '保存新版本' }}
            </ElButton>
          </div>
        </header>

        <slot name="messages" />

        <div class="studio-canvas-body">
          <slot v-if="readonlyView && $slots.readonly" name="readonly" />
          <slot v-else />
        </div>
      </div>
    </section>
  </div>
</template>
