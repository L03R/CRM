<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">角色管理</h2>
        <p class="page-desc">维护角色、数据范围及菜单和操作权限</p>
      </div>
      <el-button v-if="userStore.hasPerm('system:role:add')" type="primary" @click="openForm()">新增角色</el-button>
    </div>
    <el-alert title="内置系统管理员角色受保护；数据范围配置将在对应业务模块查询中使用。" type="info" :closable="false" />
    <div class="card">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索角色名称 / 编码" clearable style="width:260px" @keyup.enter="load" />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="keyword = ''; load()">重置</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding:12px 24px">
              <el-tag v-for="p in granted(row)" :key="p.id" effect="plain" style="margin:4px">{{ p.permissionName }}</el-tag>
              <span v-if="!row.permissionIds.length">未授权</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="roleName" label="角色名称" width="150" />
        <el-table-column prop="roleCode" label="角色编码" width="190" />
        <el-table-column label="数据范围" width="150">
          <template #default="{ row }">{{ scopes[row.dataScope] }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="members" label="在职成员数" width="110" />
        <el-table-column label="菜单权限" min-width="240">
          <template #default="{ row }">{{ granted(row).filter(p => p.permissionType === 2).map(p => p.permissionName).join('、') || '无' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button v-if="userStore.hasPerm('system:role:edit')" link type="primary" :disabled="!!row.isBuiltin" @click="openForm(row)">编辑</el-button>
            <el-button v-if="userStore.hasPerm('system:role:assign')" link type="primary" :disabled="!!row.isBuiltin" @click="openPermissions(row)">配置权限</el-button>
            <el-button v-if="userStore.hasPerm('system:role:delete')" link type="danger" :disabled="!!row.isBuiltin" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="formVisible" :title="form.id ? '修改角色' : '新增角色'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色名称" prop="roleName"><el-input v-model.trim="form.roleName" maxlength="64" /></el-form-item>
        <el-form-item label="角色编码" prop="roleCode"><el-input v-model.trim="form.roleCode" :disabled="!!form.id" maxlength="64" placeholder="如 GUITAR_SALES" /></el-form-item>
        <el-form-item label="数据范围" prop="dataScope"><el-select v-model="form.dataScope" style="width:100%"><el-option v-for="(label, value) in scopes" :key="value" :label="label" :value="Number(value)" /></el-select></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio :value="1">启用</el-radio><el-radio :value="0">停用</el-radio></el-radio-group></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.remark" type="textarea" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="formVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitForm">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="permissionVisible" :title="'配置权限 - ' + (selectedRole?.roleName || '')" width="560px" destroy-on-close>
      <el-alert title="独立勾选，菜单不会自动授予全部操作；保存时补齐父级菜单和查看权限。停用节点不参与保存。" type="info" :closable="false" />
      <div style="margin:12px 0"><el-button @click="selectAll">全选可用权限</el-button><el-button @click="treeRef.setCheckedKeys([])">清空</el-button></div>
      <el-tree ref="treeRef" :data="permissionTree" node-key="id" show-checkbox check-strictly default-expand-all :props="{label:'permissionName',children:'children',disabled:'disabled'}" />
      <template #footer><el-button @click="permissionVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitPermissions">保存授权</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { listRoles, listPermissions, saveRole, removeRole, assignPermissions } from '@/api/system/role'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const router = useRouter()

const rows = ref([])
const permissions = ref([])
const loading = ref(false)
const keyword = ref('')
const scopes = { 1: '全部数据', 2: '本部门及下级', 3: '本部门数据', 4: '本人数据' }
const granted = (role) => permissions.value.filter(p => role.permissionIds.includes(p.id))
const formVisible = ref(false)
const formRef = ref()
const form = ref({})
const saving = ref(false)
const permissionVisible = ref(false)
const selectedRole = ref(null)
const treeRef = ref()
const rules = {
  roleName: [{ required:true, message:'请输入角色名称' }],
  roleCode: [{ required:true, message:'请输入角色编码' }, { pattern:/^[A-Z0-9_]{2,64}$/, message:'2-64位大写字母、数字或下划线' }],
  dataScope: [{ required:true, message:'请选择数据范围' }]
}
const permissionTree = computed(() => {
  const nodes = new Map(permissions.value.map(p => [p.id, { ...p, disabled:p.status !== 1, children:[] }]))
  const roots = []
  for (const node of nodes.values()) {
    if (node.parentId == null) roots.push(node)
    else nodes.get(node.parentId)?.children.push(node)
  }
  const markDisabled = (list, parentDisabled=false) => list.forEach(node => {
    node.disabled ||= parentDisabled
    markDisabled(node.children, node.disabled)
  })
  markDisabled(roots)
  return roots
})

function openForm(role) {
  form.value = role ? { ...role } : { roleName:'', roleCode:'', dataScope:4, status:1, remark:'' }
  formVisible.value = true
}

async function refresh() {
  await userStore.loadProfile()
  if (!userStore.canVisit('/system/role')) {
    router.replace(userStore.firstMenuPath())
    return
  }
  await load()
}

async function submitForm() {
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    await saveRole(form.value)
    formVisible.value = false
    ElMessage.success('角色已保存')
    await refresh()
  } finally { saving.value = false }
}

async function onDelete(role) {
  try { await ElMessageBox.confirm('确定删除角色“' + role.roleName + '”？已关联员工的角色不能删除。', '删除角色', { type:'warning' }) } catch { return }
  await removeRole(role.id)
  ElMessage.success('角色已删除')
  await refresh()
}

async function openPermissions(role) {
  selectedRole.value = role
  permissionVisible.value = true
  await nextTick()
  treeRef.value.setCheckedKeys(role.permissionIds)
}

function selectAll() {
  const ids = []
  const visit = (nodes) => nodes.forEach(node => { if (!node.disabled) ids.push(node.id); visit(node.children) })
  visit(permissionTree.value)
  treeRef.value.setCheckedKeys(ids)
}

async function submitPermissions() {
  saving.value = true
  try {
    const ids = treeRef.value.getCheckedNodes().filter(node => !node.disabled).map(node => node.id)
    await assignPermissions(selectedRole.value.id, ids)
    permissionVisible.value = false
    ElMessage.success('权限已保存，下次请求生效')
    await refresh()
  } finally { saving.value = false }
}

async function load() {
  loading.value = true
  try {
    const [roles, catalog] = await Promise.all([listRoles(keyword.value), listPermissions()])
    rows.value = roles
    permissions.value = catalog
  } finally { loading.value = false }
}
onMounted(load)
</script>
