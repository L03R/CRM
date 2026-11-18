<template>
  <el-drawer v-model="visible" :title="category.categoryName + ' · 属性配置'" size="min(1150px, 96vw)">
    <el-alert title="子分类自动继承父分类属性；本级相同编码会覆盖继承配置。修改本级配置会影响使用它的下级分类。" type="info" :closable="false" />
    <el-tabs v-model="scope">
      <el-tab-pane label="产品属性（型号技术参数）" name="MODEL" />
      <el-tab-pane label="SKU 属性（销售规格）" name="SKU" />
    </el-tabs>
    <div class="attribute-toolbar">
      <span class="text-sub">{{ scope === 'MODEL' ? '例如桶型、材质、功率。' : '例如颜色、左右手、弦径、包装。' }}停用保留历史值，重新启用后可继续使用。</span>
      <el-button v-if="canEdit" type="primary" @click="openForm()">新增属性</el-button>
    </div>
    <el-table v-loading="loading" :data="fields" row-key="id">
      <el-table-column label="属性" min-width="150"><template #default="{row}"><div>{{ row.attributeName }}</div><small class="text-sub">{{ row.attributeCode }}</small></template></el-table-column>
      <el-table-column label="类型" width="80"><template #default="{row}">{{ types[row.inputType] }}</template></el-table-column>
      <el-table-column label="必填" width="65"><template #default="{row}">{{ row.required ? '是' : '否' }}</template></el-table-column>
      <el-table-column label="选项 / 单位" min-width="140"><template #default="{row}">{{ row.options.join('、') || row.unit || '—' }}</template></el-table-column>
      <el-table-column label="显示条件" min-width="135"><template #default="{row}">{{ conditionText(row) }}</template></el-table-column>
      <el-table-column label="来源" min-width="135"><template #default="{row}"><el-tag :type="row.inherited ? 'info' : 'primary'">{{ row.inherited ? '继承：' + row.sourceCategoryName : '本级' }}</el-tag></template></el-table-column>
      <el-table-column prop="sortNo" label="排序" width="65" />
      <el-table-column label="状态" width="75"><template #default="{row}"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column v-if="canEdit" label="操作" width="145" fixed="right"><template #default="{row}">
        <el-button link type="primary" @click="openForm(row)">{{ row.inherited ? '在本级配置' : '编辑' }}</el-button>
        <el-button v-if="!row.inherited" link type="danger" :disabled="row.used" @click="remove(row)">删除</el-button>
      </template></el-table-column>
      <template #empty>暂无属性，可新增；下级分类会自动继承。</template>
    </el-table>
    <p class="text-sub">已有数据的属性不能删除或修改类型、精度、显示条件，选择类型只能增加选项。删除未用的本级覆盖配置后，将恢复父级配置。</p>
  </el-drawer>

  <el-dialog v-model="formVisible" :title="form.id ? '编辑属性' : inheritedEdit ? '在本级配置继承属性' : '新增属性'" width="580px" append-to-body destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="属性范围"><el-radio-group v-model="form.attributeScope" :disabled="lockedCode"><el-radio value="MODEL">产品</el-radio><el-radio value="SKU">SKU</el-radio></el-radio-group></el-form-item>
      <el-form-item label="属性名称" prop="attributeName"><el-input v-model.trim="form.attributeName" maxlength="64" /></el-form-item>
      <el-form-item label="属性编码" prop="attributeCode"><el-input v-model.trim="form.attributeCode" :disabled="lockedCode" maxlength="64" placeholder="如 rated_power，保存后不可修改" /></el-form-item>
      <el-form-item label="输入方式"><el-select v-model="form.inputType" :disabled="form.used" @change="changeType"><el-option v-for="(label,key) in types" :key="key" :value="key" :label="label" /></el-select></el-form-item>
      <el-form-item v-if="form.inputType !== 'NUMBER'" :label="form.inputType === 'TEXT' ? '输入建议' : '可选项'">
        <el-input v-model="optionsText" type="textarea" :rows="4" placeholder="每行一个选项。文本的建议值可留空，也允许用户输入其他内容。" />
      </el-form-item>
      <el-form-item label="单位"><el-input v-model.trim="form.unit" maxlength="16" placeholder="可留空，如 mm、W" /></el-form-item>
      <el-form-item v-if="form.inputType === 'NUMBER'" label="小数位数"><el-input-number v-model="form.decimalPlaces" :min="0" :max="6" :precision="0" :disabled="form.used" /></el-form-item>
      <el-form-item label="是否必填"><el-switch v-model="form.required" /></el-form-item>
      <el-form-item label="显示条件">
        <el-select v-model="form.conditionCode" clearable :disabled="form.used" placeholder="不选表示始终显示" @change="form.conditionValue = null">
          <el-option v-for="field in controls" :key="field.attributeCode" :label="field.attributeName" :value="field.attributeCode" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.conditionCode" label="条件选项"><el-select v-model="form.conditionValue" :disabled="form.used" placeholder="选择条件值"><el-option v-for="value in controlOptions" :key="value" :label="value" :value="value" /></el-select></el-form-item>
      <el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="0" :max="9999" :precision="0" /></el-form-item>
      <el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio :value="1">启用</el-radio><el-radio :value="0">停用</el-radio></el-radio-group></el-form-item>
    </el-form>
    <el-alert v-if="form.used" title="已有数据：类型、精度和显示条件已锁定；可停用，或补充选择项。" type="info" :closable="false" />
    <template #footer><el-button @click="formVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { listAttributeDefinitions, saveAttributeDefinition, removeAttributeDefinition } from '@/api/product'

const store = useUserStore()
const canEdit = computed(() => store.hasPerm('product:category:edit'))
const types = { TEXT: '文本', NUMBER: '数字', SELECT: '单选', MULTI: '多选' }
const visible = ref(false), category = ref({}), scope = ref('MODEL'), templates = ref({model: [], sku: []}), loading = ref(false)
const formVisible = ref(false), formRef = ref(), form = ref({}), optionsText = ref(''), inheritedEdit = ref(false), saving = ref(false)
const fields = computed(() => templates.value[scope.value === 'MODEL' ? 'model' : 'sku'])
const lockedCode = computed(() => !!form.value.id || inheritedEdit.value)
const formFields = computed(() => templates.value[form.value.attributeScope === 'MODEL' ? 'model' : 'sku'])
const controls = computed(() => formFields.value.filter(f => f.attributeCode !== form.value.attributeCode && f.status === 1 && f.inputType === 'SELECT' && !f.conditionCode))
const controlOptions = computed(() => formFields.value.find(f => f.attributeCode === form.value.conditionCode)?.options || [])
const rules = { attributeName: [{required: true, message: '请输入属性名称'}], attributeCode: [{required: true, message: '请输入属性编码'}, {pattern: /^[a-z][a-z0-9_]{0,63}$/, message: '小写字母开头，只包含小写字母、数字和下划线'}] }

async function load() {
  loading.value = true
  try { templates.value = await listAttributeDefinitions(category.value.id) } finally { loading.value = false }
}
async function open(row) {
  category.value = row
  scope.value = 'MODEL'
  templates.value = {model: [], sku: []}
  visible.value = true
  await load()
}
function conditionText(row) {
  if (!row.conditionCode) return '始终显示'
  const control = fields.value.find(f => f.attributeCode === row.conditionCode)
  return (control?.attributeName || row.conditionCode) + ' = ' + row.conditionValue
}
function openForm(row = null) {
  inheritedEdit.value = !!row?.inherited
  form.value = row ? {...row, id: row.inherited ? null : row.id} : {attributeScope: scope.value, attributeName: '', attributeCode: '', inputType: 'TEXT', unit: '', required: false, conditionCode: null, conditionValue: null, decimalPlaces: 2, sortNo: 0, status: 1, used: false}
  optionsText.value = row?.options.join('\n') || ''
  formVisible.value = true
}
function changeType() { if (form.value.inputType === 'NUMBER') optionsText.value = '' }
async function submit() {
  try { await formRef.value.validate() } catch { return }
  const options = optionsText.value.split('\n').map(v => v.trim()).filter(Boolean)
  if (['SELECT','MULTI'].includes(form.value.inputType) && !options.length) { ElMessage.warning('请至少填写一个选项'); return }
  if (form.value.conditionCode && !form.value.conditionValue) { ElMessage.warning('请选择显示条件的选项'); return }
  saving.value = true
  try {
    await saveAttributeDefinition(category.value.id, {...form.value, options})
    scope.value = form.value.attributeScope
    formVisible.value = false
    ElMessage.success('属性配置已保存')
    await load()
  } finally { saving.value = false }
}
async function remove(row) {
  try { await ElMessageBox.confirm('删除“' + row.attributeName + '”？删除本级覆盖后会恢复父级同编码配置。', '删除属性', {type: 'warning'}) } catch { return }
  await removeAttributeDefinition(category.value.id, row.id)
  ElMessage.success('属性已删除')
  await load()
}
defineExpose({open})
</script>

<style scoped>
.attribute-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
</style>
