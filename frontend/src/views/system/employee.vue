<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">员工管理</h2>
        <p class="page-desc">查看数据库中的员工、部门归属和角色</p>
      </div>
      <el-button v-if="userStore.hasPerm('system:employee:add')" type="primary" @click="openCreate">新增员工</el-button>
    </div>
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索姓名 / 工号 / 手机号" clearable style="width:260px" @keyup.enter="load(true)" />
        <el-tree-select v-model="query.deptId" :data="deptOptions" placeholder="所属部门" clearable check-strictly style="width:180px" />
        <el-select v-model="query.status" placeholder="账号状态" clearable style="width:130px">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" @click="load(true)">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column prop="employeeNo" label="工号" width="145" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="deptName" label="所属部门" min-width="130" />
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="id in row.roleIds" :key="id" style="margin-right:6px">{{ roleNames.get(id) }}</el-tag>
            <span v-if="!row.roleIds.length">未分配</span>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="账号状态" width="100">
          <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="在职状态" width="100">
          <template #default="{ row }">{{ row.employmentStatus === 1 ? '在职' : '离职' }}</template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最近登录" min-width="180" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button v-if="userStore.hasPerm('system:employee:edit')" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="userStore.hasPerm('system:employee:assignRole')" link type="primary" @click="openRoles(row)">分配角色</el-button>
            <el-button v-if="userStore.hasPerm('system:employee:status')" link type="warning" @click="openState(row)">状态设置</el-button>
            <el-button v-if="userStore.hasPerm('system:employee:resetPwd') && row.id !== userStore.userInfo.id" :disabled="saving" link type="danger" @click="resetPassword(row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="load(true)" />
    </div>
    <el-dialog v-model="createVisible" :title="form.id ? '编辑员工' : '新增员工'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="员工编号" prop="employeeNo"><el-input v-model.trim="form.employeeNo" :disabled="!!form.id" maxlength="32" placeholder="唯一登录账号" /></el-form-item>
        <el-form-item label="姓名" prop="realName"><el-input v-model.trim="form.realName" maxlength="64" /></el-form-item>
        <el-form-item label="所属部门" prop="deptId"><el-tree-select v-model="form.deptId" :data="deptOptions" check-strictly style="width:100%" /></el-form-item>
        <el-form-item v-if="!form.id && userStore.hasPerm('system:employee:assignRole')" label="角色">
          <el-select v-model="form.roleIds" multiple clearable style="width:100%"><el-option v-for="role in assignableRoles" :key="role.id" :label="role.roleName" :value="role.id" /></el-select>
        </el-form-item>
        <el-form-item label="手机号"><el-input v-model.trim="form.phone" maxlength="20" /></el-form-item>
        <el-form-item label="邮箱" prop="email"><el-input v-model.trim="form.email" maxlength="128" /></el-form-item>
        <el-form-item label="入职日期"><el-date-picker v-model="form.hireDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" maxlength="500" /></el-form-item>
        <p v-if="!form.id" class="text-sub">系统会生成随机初始密码，创建成功后展示一次，请交给员工登录后尽快修改。</p>
      </el-form>
      <template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitCreate">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="credentialsVisible" title="员工登录凭据" width="460px" :close-on-click-modal="false" destroy-on-close @closed="credentials=null">
      <template v-if="credentials">
        <p>员工编号：<strong>{{ credentials.employeeNo }}</strong></p>
        <p>初始密码：<strong style="user-select:all">{{ credentials.initialPassword }}</strong></p>
        <el-alert title="请保存并交给员工，登录后尽快修改；关闭后无法再次查看此密码。重置密码后旧会话已失效。" type="warning" :closable="false" />
      </template>
      <template #footer><el-button type="primary" @click="credentialsVisible=false">已保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="stateVisible" :title="'账号状态 - ' + (stateForm.realName || '')" width="460px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="在职状态"><el-radio-group v-model="stateForm.employmentStatus" @change="value => { if(value === 0) stateForm.status = 0 }"><el-radio :value="1">在职</el-radio><el-radio :value="0">离职</el-radio></el-radio-group></el-form-item>
        <el-form-item label="账号状态"><el-radio-group v-model="stateForm.status"><el-radio :value="1" :disabled="stateForm.employmentStatus === 0">启用</el-radio><el-radio :value="0">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <el-alert title="离职或停用会立即退出该员工的所有会话，并解除其部门负责人任命。历史员工、角色和业务记录保留；重新启用需重新登录。" type="warning" :closable="false" />
      <template #footer><el-button @click="stateVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitState">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="rolesVisible" :title="'分配角色 - ' + (selectedUser?.realName || '')" width="460px" destroy-on-close>
      <el-select v-model="selectedRoles" multiple clearable placeholder="可选择多个角色，也可清空" style="width:100%">
        <el-option v-for="role in assignableRoles" :key="role.id" :label="role.roleName" :value="role.id" />
      </el-select>
      <p class="text-sub">保存后下一次请求生效；未分配角色的账号可以登录，但没有业务菜单。</p>
      <template #footer><el-button @click="rolesVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitRoles">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageUsers, createUser, updateUser, changeUserState, resetUserPassword, assignUserRoles, listEmployeeDepartments } from '@/api/system/user'
import { listRoles } from '@/api/system/role'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const rows = ref([])
const total = ref(0), stateVisible = ref(false), stateForm = ref({})
const loading = ref(false)
const deptOptions = ref([])
const roleNames = ref(new Map())
const query = reactive({ keyword: '', deptId: null, status: null, page: 1, size: 20 })
const roleOptions = ref([])
const isAdmin = computed(() => userStore.userInfo.roles.some(r => r.roleCode === 'SUPER_ADMIN'))
const assignableRoles = computed(() => roleOptions.value.filter(r => r.status === 1 && (r.roleCode !== 'SUPER_ADMIN' || isAdmin.value)))
const createVisible = ref(false)
const credentialsVisible = ref(false)
const credentials = ref(null)
const rolesVisible = ref(false)
const selectedUser = ref(null)
const selectedRoles = ref([])
const saving = ref(false)
const formRef = ref()
const form = ref({})
const rules = {
  employeeNo: [{ required: true, message: '请输入员工编号' }, { pattern: /^[A-Za-z0-9_-]{3,32}$/, message: '3-32位字母、数字、下划线或短横线' }],
  realName: [{ required: true, message: '请输入姓名' }],
  deptId: [{ required: true, message: '请选择所属部门' }],
  email: [{ type: 'email', message: '邮箱格式不正确' }]
}

function openCreate() {
  form.value = { employeeNo: '', realName: '', deptId: null, phone: '', email: '', hireDate: null, remark: '', roleIds: [] }
  createVisible.value = true
}
function openEdit(row) { form.value = {...row}; createVisible.value = true }
function openState(row) { stateForm.value = {...row}; stateVisible.value = true }
async function submitState() {
  saving.value = true
  try { await changeUserState(stateForm.value.id,stateForm.value); stateVisible.value=false; ElMessage.success('账号状态已更新'); await load() }
  finally { saving.value=false }
}
async function resetPassword(row) {
  try { await ElMessageBox.confirm('重置“'+row.realName+'”的密码？该员工的所有旧登录会话将立即失效。','重置密码',{type:'warning'}) } catch { return }
  saving.value=true
  try { credentials.value=await resetUserPassword(row.id); credentialsVisible.value=true }
  finally { saving.value=false }
}

async function submitCreate() {
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    if (form.value.id) {
      await updateUser(form.value.id,form.value)
      ElMessage.success('员工资料已保存')
      if (form.value.id === userStore.userInfo.id) await userStore.loadProfile()
    } else {
      credentials.value = await createUser(form.value)
      credentialsVisible.value = true
    }
    createVisible.value = false
    await load()
  } finally { saving.value = false }
}

function openRoles(user) {
  selectedUser.value = user
  selectedRoles.value = [...user.roleIds]
  rolesVisible.value = true
}

async function submitRoles() {
  saving.value = true
  try {
    await assignUserRoles(selectedUser.value.id, selectedRoles.value)
    rolesVisible.value = false
    ElMessage.success('角色已更新')
    await userStore.loadProfile()
    if (!userStore.hasPerm('system:employee:list')) {
      window.location.href = '/'
      return
    }
    await load()
  } finally { saving.value = false }
}

async function load(resetPage = false) {
  if (resetPage === true) query.page = 1
  loading.value = true
  try { const result=await pageUsers(query); rows.value=result.records; total.value=result.total }
  finally { loading.value = false }
}

function reset() {
  Object.assign(query, { keyword: '', deptId: null, status: null, page: 1 })
  load()
}

onMounted(async () => {
  const [departments, roles] = await Promise.all([listEmployeeDepartments(), listRoles()])
  const convert = (nodes) => nodes.map(d => ({ value: d.id, label: d.deptName, disabled: d.status !== 1, children: convert(d.children) }))
  deptOptions.value = convert(departments)
  roleNames.value = new Map(roles.map(r => [r.id, r.roleName]))
  roleOptions.value = roles
  await load()
})
</script>
