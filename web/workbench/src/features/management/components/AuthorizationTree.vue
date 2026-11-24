<script setup lang="ts">
import { ElCheckbox, ElTree } from 'element-plus'
import { computed } from 'vue'
import type { Menu, PermissionCode } from '../model/management'

/** 授权树保留菜单和功能的独立选择规则；选中菜单时只包含允许授予的后代。 */
const props = defineProps<{
  nodes: Menu[]
  modelValue: string[]
  keyword: string
  expanded: boolean
  readonly: boolean
  assignedMenus?: string[]
  assignedCodes?: PermissionCode[]
}>()
const emit = defineEmits<{ 'update:modelValue': [string[]] }>()

interface AuthorizationNode {
  key: string
  label: string
  description: string
  values: string[]
  disabled: boolean
  children: AuthorizationNode[]
}

function visible(node: Menu): boolean {
  if (props.assignedMenus && !props.assignedMenus.includes(node.menuId))
    return false
  return (
    !props.keyword ||
    node.name.includes(props.keyword) ||
    node.functions.some(
      (fn) =>
        fn.name.includes(props.keyword) || fn.code.includes(props.keyword),
    ) ||
    node.children.some(visible)
  )
}

function keys(node: Menu): string[] {
  const result =
    (node.selectable || !!props.assignedMenus) &&
    (!props.assignedMenus || props.assignedMenus.includes(node.menuId))
      ? ['m:' + node.menuId]
      : []
  for (const fn of node.functions) {
    if (
      (fn.selectable || !!props.assignedCodes) &&
      (!props.assignedCodes || props.assignedCodes.includes(fn.code))
    )
      result.push('f:' + fn.code)
  }
  for (const child of node.children) result.push(...keys(child))
  return result
}

function build(nodes: Menu[]): AuthorizationNode[] {
  return nodes.filter(visible).map((node) => {
    const functions = node.functions
      .filter(
        (fn) =>
          (!props.assignedCodes || props.assignedCodes.includes(fn.code)) &&
          (!props.keyword ||
            node.name.includes(props.keyword) ||
            fn.name.includes(props.keyword) ||
            fn.code.includes(props.keyword)),
      )
      .map((fn) => ({
        key: 'f:' + fn.code,
        label: fn.name,
        description: fn.disabledReason || fn.code,
        values: ['f:' + fn.code],
        disabled: !props.assignedCodes && !fn.selectable,
        children: [],
      }))
    const values = keys(node)
    return {
      key: 'm:' + node.menuId,
      label: node.name,
      description: node.status === 'DISABLED' ? '已禁用' : '',
      values,
      disabled: !values.length,
      children: [...build(node.children), ...functions],
    }
  })
}

const tree = computed(() => build(props.nodes))
function selected(values: string[]) {
  return (
    values.length > 0 && values.every((key) => props.modelValue.includes(key))
  )
}
function partial(values: string[]) {
  return (
    !selected(values) && values.some((key) => props.modelValue.includes(key))
  )
}
function toggle(values: string[], checked: boolean) {
  const set = new Set(props.modelValue)
  for (const value of values) {
    if (checked) set.add(value)
    else set.delete(value)
  }
  emit('update:modelValue', [...set])
}
</script>

<template>
  <ElTree
    :key="`${expanded}-${keyword}`"
    :data="tree"
    node-key="key"
    :default-expand-all="expanded || Boolean(keyword)"
    :expand-on-click-node="false"
    empty-text="暂无菜单或功能"
    class="management-authorization-tree"
  >
    <template #default="{ data }">
      <ElCheckbox
        :model-value="selected(data.values)"
        :indeterminate="partial(data.values)"
        :disabled="readonly || data.disabled"
        :aria-label="data.label"
        @click.stop
        @change="toggle(data.values, Boolean($event))"
      >
        <span :title="data.description"
          >{{ data.label }}
          <small class="management-muted">{{ data.description }}</small></span
        >
      </ElCheckbox>
    </template>
  </ElTree>
</template>
