<template>
  <div class="page">
    <div class="page-header">
      <div><h2 class="page-title">权限管理</h2><p class="page-desc">维护目录、菜单和操作权限；新增权限不自动生成业务功能</p></div>
      <el-button v-if="userStore.hasPerm('system:permission:add')" type="primary" @click="openCreate()">新增权限</el-button>
    </div>
    <el-alert title="权限编码和类型创建后不能修改。隐藏仅影响导航，停用会使整个权限分支失效。关键管理入口受保护。" type="info" :closable="false" />
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索权限名称 / 编码" clearable style="width:260px" />
        <el-select v-model="query.type" placeholder="权限类型" clearable style="width:140px"><el-option v-for="(label, value) in types" :key="value" :label="label" :value="Number(value)" /></el-select>
        <el-button @click="query.keyword='';query.type=null">重置</el-button>
        <el-button @click="load">刷新</el-button>
        <el-button @click="expandAll=!expandAll">{{ expandAll ? '全部收起' : '全部展开' }}</el-button>
      </div>
      <el-table :key="expandAll" v-loading="loading" :data="rows" row-key="id" :default-expand-all="expandAll">
        <el-table-column prop="permissionName" label="权限名称" min-width="200"><template #default="{row}">{{ row.permissionName }} <el-tag v-if="row.protectedNode" size="small" type="info">受保护</el-tag></template></el-table-column>
        <el-table-column label="类型" width="85"><template #default="{row}">{{ types[row.permissionType] }}</template></el-table-column>
        <el-table-column prop="permissionCode" label="权限编码" min-width="230" />
        <el-table-column prop="routePath" label="页面路由" min-width="170" />
        <el-table-column prop="sortNo" label="排序" width="70" />
        <el-table-column label="显示" width="85"><template #default="{row}">{{ row.permissionType === 3 ? '—' : row.visible === 1 ? '显示' : '隐藏' }}</template></el-table-column>
        <el-table-column label="状态" width="85"><template #default="{row}"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="280" fixed="right"><template #default="{row}">
          <el-button v-if="row.permissionType !== 3 && userStore.hasPerm('system:permission:add')" link type="primary" @click="openCreate(row)">新增下级</el-button>
          <el-button v-if="userStore.hasPerm('system:permission:edit')" link type="primary" :disabled="row.protectedNode" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="userStore.hasPerm('system:permission:status')" link type="warning" :disabled="row.protectedNode" @click="onStatus(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
          <el-button v-if="userStore.hasPerm('system:permission:delete')" link type="danger" :disabled="row.protectedNode" @click="onDelete(row)">删除</el-button>
        </template></el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑权限' : '新增权限'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="类型"><el-radio-group v-model="form.permissionType" :disabled="!!form.id" @change="onTypeChange"><el-radio v-for="(label,value) in types" :key="value" :value="Number(value)">{{ label }}</el-radio></el-radio-group></el-form-item>
        <el-form-item label="权限名称" prop="permissionName"><el-input v-model.trim="form.permissionName" maxlength="64" /></el-form-item>
        <el-form-item label="权限编码" prop="permissionCode"><el-input v-model.trim="form.permissionCode" :disabled="!!form.id" maxlength="128" placeholder="菜单 menu:example；操作 example:export" /></el-form-item>
        <el-form-item label="上级节点" prop="parentId" :rules="form.permissionType === 3 ? [{required:true,message:'操作必须选择上级菜单'}] : []"><el-tree-select v-model="form.parentId" :data="parentOptions" check-strictly clearable style="width:100%" placeholder="目录或菜单可以作为顶级节点" /></el-form-item>
        <template v-if="form.permissionType !== 3">
          <el-form-item label="页面路由" prop="routePath" :rules="form.permissionType === 2 ? [{required:true,message:'请输入页面路由'}] : []"><el-input v-model.trim="form.routePath" maxlength="255" placeholder="如 /sales/customers" /></el-form-item>
          <el-form-item v-if="form.permissionType === 2" label="页面组件" prop="component" :rules="[{required:true,message:'请选择已有页面'}]"><el-select v-model="form.component" style="width:100%"><el-option v-for="(label,key) in components" :key="key" :value="key" :label="label + '（' + key + '）'" /></el-select></el-form-item>
          <el-form-item label="图标"><el-input v-model.trim="form.icon" maxlength="64" placeholder="如 User、Setting、Key" /></el-form-item>
          <el-form-item label="导航显示"><el-radio-group v-model="form.visible"><el-radio :value="1">显示</el-radio><el-radio :value="0">隐藏</el-radio></el-radio-group></el-form-item>
        </template>
        <el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="0" :max="9999" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio :value="1">启用</el-radio><el-radio :value="0">停用</el-radio></el-radio-group></el-form-item>
        <p class="text-sub">新增后自动授予系统管理员；普通角色需要到角色管理中授权。操作仍需由实际按钮和后端接口使用对应编码。</p>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { listPermissionTree, listComponents, savePermission, changePermissionStatus, removePermission } from '@/api/system/permission'

const userStore = useUserStore()
const types = { 1:'目录', 2:'菜单', 3:'操作' }
const tree = ref([])
const components = ref({})
const loading = ref(false)
const saving = ref(false)
const expandAll = ref(true)
const query = reactive({keyword:'',type:null})
const dialogVisible = ref(false)
const formRef = ref()
const form = ref({})
const rules = {
  permissionName:[{required:true,message:'请输入权限名称'}],
  permissionCode:[{required:true,message:'请输入权限编码'},{pattern:/^[a-z][a-z0-9]*(?::[A-Za-z0-9_-]+)+$/,message:'使用冒号分隔，如 customer:export'}]
}
const rows = computed(() => {
  const keyword = query.keyword.trim().toLowerCase()
  const filter = (nodes) => nodes.flatMap(node => {
    const children = filter(node.children)
    const match = (!query.type || query.type === node.permissionType) &&
      (!keyword || node.permissionName.toLowerCase().includes(keyword) || node.permissionCode.toLowerCase().includes(keyword))
    return match || children.length ? [{...node,children}] : []
  })
  return filter(tree.value)
})
const parentOptions = computed(() => {
  const convert = (nodes, excluded=false) => nodes.filter(n => form.value.permissionType === 3 ? n.permissionType !== 3 : n.permissionType === 1).map(node => {
    const unavailable = excluded || node.id === form.value.id
    return {value:node.id,label:node.permissionName,disabled:unavailable || node.permissionType !== (form.value.permissionType === 3 ? 2 : 1),children:convert(node.children,unavailable)}
  })
  return convert(tree.value)
})

function openCreate(parent=null) {
  const type = parent ? parent.permissionType === 2 ? 3 : 2 : 1
  form.value = {permissionName:'',permissionCode:type === 3 ? '' : 'menu:',permissionType:type,parentId:parent?.id ?? null,routePath:'',component:null,icon:'',sortNo:0,visible:1,status:1}
  dialogVisible.value = true
}
function openEdit(row) { form.value = {...row}; dialogVisible.value = true }
function onTypeChange() {
  form.value.parentId = null
  form.value.permissionCode = form.value.permissionType === 3 ? '' : 'menu:'
}
async function load() {
  loading.value = true
  try { [tree.value,components.value] = await Promise.all([listPermissionTree(),listComponents()]) }
  finally { loading.value = false }
}
async function refresh() { await userStore.loadProfile(); await load() }
async function submit() {
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    await savePermission(form.value)
    dialogVisible.value = false
    ElMessage.success('权限已保存')
    await refresh()
  } finally { saving.value = false }
}
async function onStatus(row) {
  const label = row.status === 1 ? '停用' : '启用'
  try { await ElMessageBox.confirm('确定' + label + '“' + row.permissionName + '”？停用后整个分支的权限将失效。','确认状态修改',{type:'warning'}) } catch { return }
  await changePermissionStatus(row.id,1-row.status)
  ElMessage.success('状态已更新')
  await refresh()
}
async function onDelete(row) {
  try { await ElMessageBox.confirm('确定删除“' + row.permissionName + '”？必须无下级且无其他角色引用，管理员自动授权将一起清理。','删除权限',{type:'warning'}) } catch { return }
  await removePermission(row.id)
  ElMessage.success('权限已删除')
  await refresh()
}
onMounted(load)
</script>
