<script setup lang="ts">
import { computed } from 'vue'
import type { Menu, PermissionCode } from '../model/management'
const props = defineProps<{ nodes: Menu[]; modelValue: string[]; keyword: string; expanded: boolean; readonly: boolean; assignedMenus?: string[]; assignedCodes?: PermissionCode[] }>()
const emit = defineEmits<{ 'update:modelValue': [string[]] }>()
function visible(node: Menu): boolean {
  const assigned = !props.assignedMenus || props.assignedMenus.includes(node.menuId)
  if (!assigned) return false
  return !props.keyword || node.name.includes(props.keyword) || node.functions.some(fn => fn.name.includes(props.keyword) || fn.code.includes(props.keyword)) || node.children.some(visible)
}
const nodes = computed(() => props.nodes.filter(visible))
function keys(node: Menu): string[] {
  const result = (node.selectable || !!props.assignedMenus) && (!props.assignedMenus || props.assignedMenus.includes(node.menuId)) ? ['m:' + node.menuId] : []
  for (const fn of node.functions) if ((fn.selectable || !!props.assignedCodes) && (!props.assignedCodes || props.assignedCodes.includes(fn.code))) result.push('f:' + fn.code)
  for (const child of node.children) result.push(...keys(child))
  return result
}
function selected(node: Menu) { const all = keys(node); return all.length > 0 && all.every(key => props.modelValue.includes(key)) }
function partial(node: Menu) { return !selected(node) && keys(node).some(key => props.modelValue.includes(key)) }
function toggle(values: string[], checked: boolean) {
  const set = new Set(props.modelValue)
  for (const value of values) { if (checked) set.add(value); else set.delete(value) }
  emit('update:modelValue', [...set])
}
</script>
<template>
  <ul class="authorization-tree">
    <li v-for="node in nodes" :key="node.menuId">
      <details :open="expanded || !!keyword"><summary><input type="checkbox" :aria-label="node.name" :checked="selected(node)" :indeterminate="partial(node)" :disabled="readonly || !keys(node).length" @click.stop @change="toggle(keys(node), ($event.target as HTMLInputElement).checked)" /><span>{{ node.name }}</span><small v-if="node.status === 'DISABLED'">已禁用</small></summary>
        <AuthorizationTree v-if="node.children.length" :nodes="node.children" :model-value="modelValue" :keyword="keyword" :expanded="expanded" :readonly="readonly" :assigned-menus="assignedMenus" :assigned-codes="assignedCodes" @update:model-value="emit('update:modelValue', $event)" />
        <label v-for="fn in node.functions.filter(fn => (!assignedCodes || assignedCodes.includes(fn.code)) && (!keyword || node.name.includes(keyword) || fn.name.includes(keyword) || fn.code.includes(keyword)))" :key="fn.code" :title="fn.disabledReason || fn.code"><input type="checkbox" :checked="modelValue.includes('f:' + fn.code)" :disabled="readonly || (!assignedCodes && !fn.selectable)" @change="toggle(['f:' + fn.code], ($event.target as HTMLInputElement).checked)" /><span>{{ fn.name }}<small>{{ fn.code }}</small></span></label>
      </details>
    </li>
  </ul>
</template>
