<script setup lang="ts">
import { ElButton } from 'element-plus'
import { computed } from 'vue'

export interface EditorSectionAnchor {
  key: string
  no: string
  label: string
  hidden?: boolean
}

export interface AsideMetaItem {
  label: string
  value: string
  code?: boolean
}

/**
 * 统一分块配置编辑框架（对标 wanopp.wanmol.com/submit）：
 * 页头眉标 + 左侧编号分块白卡顺滑浏览 + 右侧 Sticky 就绪度伴随栏 + 底部悬浮毛玻璃操作条。
 */
const readonlyView = defineModel<boolean>('readonlyView', { default: false })

const props = defineProps<{
  backPath: string
  backLabel: string
  eyebrow: string
  title: string
  code?: string
  sections: EditorSectionAnchor[]
  readinessDone: number
  readinessTotal: number
  missingItems?: string[]
  metaItems?: AsideMetaItem[]
  tips?: string[]
  dirty?: boolean
  canSave?: boolean
  canToggleReadonly?: boolean
  saving?: boolean
  saveLabel?: string
}>()

const emit = defineEmits<{ save: [] }>()

const progressPercent = computed(() => {
  if (!props.readinessTotal) return 100
  return Math.min(100, Math.round((props.readinessDone / props.readinessTotal) * 100))
})

const visibleSections = computed(() => props.sections.filter(s => !s.hidden))

function jumpTo(key: string) {
  const el = document.getElementById(`sec-${key}`)
  if (el && typeof el.scrollIntoView === 'function') {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}
</script>

<template>
  <div class="mp-editor-wrap">
    <!-- 页头：返回链 + 眉标 + 大标题 + 状态徽章 -->
    <header class="mp-ph">
      <div>
        <div class="mp-ph-eyebrow">
          <RouterLink :to="backPath" style="color: #667085">← 返回{{ backLabel }}</RouterLink>
          <span style="margin: 0 6px">·</span>
          <span>{{ eyebrow }}</span>
        </div>
        <h1 class="mp-ph-title">{{ title }}</h1>
      </div>
      <div class="mp-ph-meta">
        <span v-if="code" class="management-code">{{ code }}</span>
        <slot name="status" />
      </div>
    </header>

    <slot name="messages" />

    <!-- 主体两栏：左侧分块卡片流 + 右侧 Sticky 伴随栏 -->
    <div class="mp-form-shell">
      <div class="mp-form-col">
        <template v-if="readonlyView && $slots.readonly">
          <div class="mp-sec-card">
            <div class="mp-sec-body">
              <slot name="readonly" />
            </div>
          </div>
        </template>
        <slot v-else />
      </div>

      <aside class="mp-aside-col" aria-label="配置概览与导航">
        <!-- 伴随卡 1：就绪度进度与关键状态 -->
        <section class="mp-aside-card">
          <div class="mp-pv-head">
            <span>配置就绪度</span>
            <span class="mp-pv-count">{{ readinessDone }}/{{ readinessTotal }}</span>
          </div>
          <div class="mp-pv-track" role="progressbar" :aria-valuenow="progressPercent" aria-valuemin="0" aria-valuemax="100">
            <div class="mp-pv-fill" :style="{ width: `${progressPercent}%` }" />
          </div>

          <ul v-if="missingItems?.length" class="mp-tips-list" style="margin-top: 2px; color: #b54708">
            <li v-for="(item, idx) in missingItems" :key="idx">待补齐：{{ item }}</li>
          </ul>
          <div v-else style="font-size: 12.5px; color: #067647; font-weight: 500">
            必填项已完整，保存后即为“已就绪”状态
          </div>

          <div v-if="metaItems?.length" class="mp-pv-list">
            <div v-for="item in metaItems" :key="item.label" class="mp-pv-row">
              <span class="mp-pv-label">{{ item.label }}</span>
              <span class="mp-pv-value" :class="{ 'management-code': item.code }">{{ item.value }}</span>
            </div>
          </div>

          <slot name="aside-extra" />
        </section>

        <!-- 伴随卡 2：分块快速定位 + 可折叠小贴士 -->
        <section class="mp-aside-card is-subtle">
          <nav v-if="!readonlyView && visibleSections.length" class="mp-anchor-nav" aria-label="快速定位">
            <button
              v-for="sec in visibleSections"
              :key="sec.key"
              type="button"
              class="mp-anchor-btn"
              @click="jumpTo(sec.key)"
            >
              <span class="mp-anchor-no">{{ sec.no }}</span>
              <span>{{ sec.label }}</span>
            </button>
          </nav>

          <details v-if="tips?.length" class="mp-tips-details" open>
            <summary>
              <span>配置小贴士</span>
              <span style="font-size: 11px; color: #98a2b3">展开 / 收起</span>
            </summary>
            <ul class="mp-tips-list">
              <li v-for="(tip, idx) in tips" :key="idx">{{ tip }}</li>
            </ul>
          </details>
        </section>
      </aside>
    </div>

    <!-- 底部悬浮毛玻璃操作条 -->
    <footer class="mp-form-foot">
      <div class="mp-foot-status">
        <span class="mp-foot-dot" :class="{ 'is-dirty': dirty }" />
        <span>{{ dirty ? '有未保存改动（保存不阻断未填项，将生成新版本）' : '当前内容已与最新配置同步' }}</span>
      </div>
      <div class="mp-foot-actions">
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
    </footer>
  </div>
</template>
