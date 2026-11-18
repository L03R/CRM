<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">部门管理</h2>
        <p class="page-desc">对应 sys_dept：多级部门、负责人、排序与启用状态</p>
      </div>
      <el-button
        v-if="userStore.hasPerm('system:dept:add')"
        type="primary"
        :icon="Plus"
        @click="openDialog(null)"
      >新增部门</el-button>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索部门名称 / 编码"
          :prefix-icon="Search"
          clearable
          style="width: 260px"
        />
        <el-button type="primary" plain :icon="Search">查询</el-button>
        <el-button :icon="Refresh" @click="keyword = ''">重置</el-button>
        <div class="toolbar-right">
          <el-button :icon="Sort" @click="expandAll = !expandAll; reloadTable()">
            {{ expandAll ? '全部收起' : '全部展开' }}
          </el-button>
        </div>
      </div>

      <el-table
        v-if="tableVisible"
        v-loading="loading"
        :data="tree"
        style="width: 100%"
        row-key="id"
        :default-expand-all="expandAll"
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="deptName" label="部门名称" min-width="240">
          <template #default="{ row }">
            <div class="cell-dept">
              <el-icon :style="{ color: row.parentId ? '#98a2b3' : '#2f6bff' }">
                <component :is="row.parentId ? 'Document' : 'OfficeBuilding'" />
              </el-icon>
              <span :class="{ 'is-root': !row.parentId }">{{ row.deptName }}</span>
              <el-tag v-if="row.status === 0" type="danger" size="small" effect="plain">停用</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="deptCode" label="部门编码" width="190">
          <template #default="{ row }">
            <span class="mono text-sub">{{ row.deptCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="leaderName" label="负责人" width="110">
          <template #default="{ row }">{{ row.leaderName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="memberCount" label="在职人数" width="100" align="center" />
        <el-table-column prop="sortNo" label="排序" width="80" align="center" />
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="150" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="userStore.hasPerm('system:dept:add')"
              link type="primary" size="small"
              @click="openDialog(row, true)"
            >新增下级</el-button>
            <el-button
              v-if="userStore.hasPerm('system:dept:edit')"
              link type="primary" size="small"
              @click="openDialog(row)"
            >编辑</el-button>
            <el-button v-if="userStore.hasPerm('system:dept:delete')" link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增 / 编辑部门 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑部门' : form.parentId ? '新增下级部门' : '新增部门'"
      width="500px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="上级部门" prop="parentId">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            placeholder="不选则为一级部门"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model.trim="form.deptName" placeholder="如 华东销售一部" />
        </el-form-item>
        <el-form-item label="部门编码" prop="deptCode">
          <el-input v-model.trim="form.deptCode" placeholder="如 DEPT_SALES_EAST_1" />
        </el-form-item>
        <el-form-item label="负责人" prop="leaderUserId">
          <el-select v-model="form.leaderUserId" placeholder="选填" clearable filterable style="width: 100%">
            <el-option
              v-for="u in employeeOptions"
              :key="u.id"
              :label="`${u.realName}（${u.employeeNo}）`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="显示排序" prop="sortNo">
          <el-input-number v-model="form.sortNo" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Search, Refresh, Plus, Sort } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { listDeptTree, saveDept, removeDept } from '@/api/system/dept'
import { employeeOptions as loadEmployeeOptions } from '@/api/system/user'

const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const expandAll = ref(true)
const tableVisible = ref(true)
const tree = ref([])
const employeeOptions = ref([])

const deptTreeRaw = ref([])

async function load() {
  loading.value = true
  try {
    tree.value = filterTree(await listDeptTree(), keyword.value.trim())
    deptTreeRaw.value = tree.value
  } finally {
    loading.value = false
  }
}

function filterTree(nodes, kw) {
  if (!kw) return nodes
  const walk = (list) =>
    list
      .map((n) => {
        const children = walk(n.children || [])
        const match = n.deptName.includes(kw) || n.deptCode.toLowerCase().includes(kw.toLowerCase())
        if (match || children.length) return { ...n, children }
        return null
      })
      .filter(Boolean)
  return walk(nodes)
}

function reloadTable() {
  // 切换展开状态时重建表格，让 default-expand-all 生效
  tableVisible.value = false
  requestAnimationFrame(() => (tableVisible.value = true))
}

onMounted(async () => {
  load()
  if (userStore.hasPerm('system:dept:add') || userStore.hasPerm('system:dept:edit') || userStore.hasPerm('system:employee:list')) {
    employeeOptions.value = await loadEmployeeOptions()
  }
})

const dialogVisible = ref(false)
const formRef = ref()
const form = ref({})

const parentOptions = computed(() => {
  const convert = (nodes) =>
    nodes.map((d) => ({
      value: d.id,
      label: d.deptName,
      children: convert(d.children || [])
    }))
  return convert(deptTreeRaw.value)
})

const rules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  deptCode: [
    { required: true, message: '请输入部门编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{2,64}$/, message: '字母、数字与下划线', trigger: 'blur' }
  ]
}

function openDialog(row, asChild = false) {
  form.value = row && !asChild
    ? { ...row }
    : {
        parentId: row && asChild ? row.id : null,
        deptName: '',
        deptCode: '',
        leaderUserId: null,
        sortNo: 0,
        status: 1,
        remark: ''
      }
  dialogVisible.value = true
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await saveDept({ ...form.value })
    dialogVisible.value = false
    ElMessage.success('保存成功')
    load()
  } catch (err) {
    ElMessage.error(err?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除部门「${row.deptName}」吗？删除后不可恢复。`,
      '删除部门',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await removeDept(row.id)
    ElMessage.success('删除成功')
    load()
  } catch (err) {
    ElMessage.error(err?.message || '删除失败')
  }
}
</script>

<style scoped>
.cell-dept {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cell-dept .is-root {
  font-weight: 600;
}
</style>
