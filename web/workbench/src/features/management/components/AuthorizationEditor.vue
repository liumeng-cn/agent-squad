<script setup lang="ts">
import { ElAlert, ElButton, ElInput } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useManagementStore } from '../stores/management'
import ManagementDialog from './ManagementDialog.vue'
import AuthorizationTree from './AuthorizationTree.vue'
import {
  authorizationTree,
  roleAuthorization,
  saveAuthorization,
} from '../api/managementApi'
import type { Menu, Role, PermissionCode } from '../model/management'
import { errorMessage } from '../../../shared/http/http'
import { confirmManagement } from './confirmManagement'
const props = defineProps<{ role: Role }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const role = ref(props.role)
const tree = ref<Menu[]>([])
const menus = ref<string[]>([])
const codes = ref<PermissionCode[]>([])
const left = ref<string[]>([])
const right = ref<string[]>([])
const keyword = ref('')
const busy = ref(false)
const loading = ref(true)
const error = ref('')
const initial = ref('')
const expanded = ref(true)
const dirty = computed(
  () => initial.value !== JSON.stringify([menus.value, codes.value]),
)
const access = useManagementStore()
const readonly = computed(
  () => role.value.builtIn || !access.can('management:role:update'),
)
onMounted(async () => {
  try {
    role.value = await roleAuthorization(props.role.roleId)
    tree.value = await authorizationTree()
    menus.value = [...(role.value.draftMenuIds || [])]
    codes.value = [...(role.value.draftPermissionCodes || [])]
    initial.value = JSON.stringify([menus.value, codes.value])
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
})
function flatten(nodes: Menu[]): Menu[] {
  return nodes.flatMap((node) => [node, ...flatten(node.children)])
}
const catalog = computed(() => flatten(tree.value))
function assign() {
  const selectedMenus = new Set(menus.value)
  const selectedCodes = new Set(codes.value)
  for (const key of left.value) {
    if (key.startsWith('m:')) selectedMenus.add(key.slice(2))
    else selectedCodes.add(key.slice(2) as PermissionCode)
  }
  for (const menu of catalog.value) {
    if (menu.functions.some((fn) => selectedCodes.has(fn.code))) {
      selectedMenus.add(menu.menuId)
      const query = menu.functions.find((fn) => fn.code.endsWith(':query'))
      if (query) selectedCodes.add(query.code)
    }
  }
  for (const id of [...selectedMenus]) {
    let parent = catalog.value.find((menu) => menu.menuId === id)?.parentMenuId
    while (parent) {
      selectedMenus.add(parent)
      parent = catalog.value.find(
        (menu) => menu.menuId === parent,
      )?.parentMenuId
    }
  }
  menus.value = [...selectedMenus]
  codes.value = [...selectedCodes]
  left.value = []
}
async function remove() {
  if (
    !right.value.length ||
    !(await confirmManagement(
      `移除选中的 ${right.value.length} 项授权？菜单下的功能会一并移除。`,
    ))
  )
    return
  const removedMenus = new Set(
    right.value
      .filter((key) => key.startsWith('m:'))
      .map((key) => key.slice(2)),
  )
  const removedCodes = new Set(
    right.value
      .filter((key) => key.startsWith('f:'))
      .map((key) => key.slice(2)),
  )
  for (const menu of catalog.value) {
    if (
      removedMenus.has(menu.menuId) ||
      removedCodes.has(
        menu.functions.find((fn) => fn.code.endsWith(':query'))?.code || '',
      )
    ) {
      for (const fn of menu.functions) removedCodes.add(fn.code)
    }
  }
  menus.value = menus.value.filter((id) => !removedMenus.has(id))
  codes.value = codes.value.filter((code) => !removedCodes.has(code))
  right.value = []
}
async function save() {
  busy.value = true
  error.value = ''
  try {
    await saveAuthorization(role.value, menus.value, codes.value)
    emit('saved')
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <ManagementDialog
    :title="`${readonly ? '查看' : '分配'}菜单与功能 · ${role.name}`"
    :wide="true"
    :dirty="dirty && !loading"
    :busy="busy"
    @close="emit('close')"
  >
    <div class="authorization-tools">
      <ElInput
        v-model="keyword"
        placeholder="搜索菜单或功能"
        aria-label="搜索菜单或功能"
      /><ElButton native-type="button" @click="expanded = !expanded">{{
        expanded ? '收起全部' : '展开全部'
      }}</ElButton
      ><span class="management-muted"
        >已选 {{ menus.length }} 个菜单 · {{ codes.length }} 项功能</span
      >
    </div>
    <p v-if="loading" role="status">正在加载授权…</p>
    <div v-else class="authorization-transfer">
      <section>
        <header>
          <strong>全部菜单与功能</strong
          ><ElButton
            native-type="button"
            v-if="!readonly"
            :disabled="busy || !left.length"
            @click="assign"
            >分配 →</ElButton
          >
        </header>
        <AuthorizationTree
          :nodes="tree"
          v-model="left"
          :keyword="keyword"
          :expanded="expanded"
          :readonly="readonly || busy"
        />
      </section>
      <section>
        <header>
          <strong>已分配菜单与功能</strong
          ><ElButton
            native-type="button"
            v-if="!readonly"
            :disabled="busy || !right.length"
            @click="remove"
            >← 移除</ElButton
          >
        </header>
        <AuthorizationTree
          :nodes="tree"
          v-model="right"
          :keyword="keyword"
          :expanded="expanded"
          :readonly="readonly || busy"
          :assigned-menus="menus"
          :assigned-codes="codes"
        />
      </section>
    </div>
    <p class="management-help">
      勾选后点击分配或移除。保存只更新草稿，应用后生效。写操作同时依赖所属页面的查询功能。
    </p>
    <ElAlert
      v-if="error"
      class="management-error"
      role="alert"
      type="error"
      :closable="false"
      show-icon
      >{{ error }}</ElAlert
    >
    <p v-if="readonly" class="management-help">
      内置平台管理员的授权不可修改。
    </p>
    <p v-else class="management-muted">
      已应用：{{ role.appliedMenuIds?.length || 0 }} 个菜单，{{
        role.appliedPermissionCodes?.length || 0
      }}
      项功能；当前草稿：新增
      {{
        codes.filter((code) => !role.appliedPermissionCodes?.includes(code))
          .length
      }}
      项，移除
      {{
        (role.appliedPermissionCodes || []).filter(
          (code) => !codes.includes(code),
        ).length
      }}
      项功能。
    </p>
    <template #actions
      ><ElButton
        type="primary"
        native-type="button"
        v-if="!readonly"
        class="management-primary"
        :disabled="busy || loading || !dirty || !initial"
        @click="save"
        >保存草稿</ElButton
      ></template
    >
  </ManagementDialog>
</template>
