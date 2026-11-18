<template>
  <div class="page">
    <div class="page-header"><div><h2 class="page-title">产品分类</h2><p class="page-desc">整琴、拾音器、吉他音箱及配件的多级目录</p></div><el-button v-if="store.hasPerm('product:category:add')" type="primary" @click="openForm()">新增分类</el-button></div>
    <div class="card">
      <div class="toolbar"><el-input v-model="keyword" placeholder="搜索名称 / 编码" clearable style="width:260px" /><el-button @click="load">刷新</el-button></div>
      <el-table v-loading="loading" :data="filtered" row-key="id" default-expand-all>
        <el-table-column prop="categoryName" label="分类名称" min-width="220" />
        <el-table-column prop="categoryCode" label="分类编码" min-width="210" />
        <el-table-column prop="sortNo" label="排序" width="90" />
        <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.status===1?'success':'info'">{{row.status===1?'启用':'停用'}}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="315"><template #default="{row}">
          <el-button v-if="store.hasPerm('product:category:list')" link type="primary" @click="attributePanel.open(row)">属性配置</el-button>
          <el-button v-if="store.hasPerm('product:category:add')" link type="primary" @click="openForm(null,row.id)">新增下级</el-button>
          <el-button v-if="store.hasPerm('product:category:edit')" link type="primary" @click="openForm(row)">编辑</el-button>
          <el-button v-if="store.hasPerm('product:category:delete')" link type="danger" @click="onDelete(row)">删除</el-button>
        </template></el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="visible" :title="form.id?'编辑分类':'新增分类'" width="470px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="分类名称" prop="categoryName"><el-input v-model.trim="form.categoryName" maxlength="64" /></el-form-item>
        <el-form-item label="分类编码" prop="categoryCode"><el-input v-model.trim="form.categoryCode" :disabled="!!form.id" maxlength="64" placeholder="如 ACOUSTIC_FOLK" /></el-form-item>
        <el-form-item label="上级分类"><el-tree-select v-model="form.parentId" :data="parentOptions" check-strictly clearable style="width:100%" placeholder="不选表示顶级分类" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="0" :max="9999" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio :value="1">启用</el-radio><el-radio :value="0">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <p class="text-sub">产品只能归入末级分类；已有产品的分类不能删除或新增下级。</p>
      <p v-if="form.id" class="text-sub">含有产品的分类分支不能更换上级；请先迁移产品，以免改变其属性模板。</p>
      <p v-if="!form.id" class="text-sub">保存后进入属性配置，继承父级属性或添加该分类的产品、SKU 属性。</p>
      <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>
    <CategoryAttributes ref="attributePanel" />
  </div>
</template>

<script setup>
import {computed,onMounted,ref} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import {useUserStore} from '@/store/user'
import {listCategories,saveCategory,removeCategory} from '@/api/product'
import CategoryAttributes from '@/components/CategoryAttributes.vue'

const store=useUserStore()
const attributePanel=ref()
const tree=ref([]), keyword=ref(''), loading=ref(false), saving=ref(false), visible=ref(false), formRef=ref(), form=ref({})
const rules={categoryName:[{required:true,message:'请输入分类名称'}],categoryCode:[{required:true,message:'请输入分类编码'},{pattern:/^[A-Z0-9_-]{2,64}$/,message:'2-64位大写字母、数字、下划线或短横线'}]}
const filtered=computed(()=>{
  const key=keyword.value.trim().toLowerCase()
  const visit=(nodes)=>nodes.flatMap(n=>{
    const children=visit(n.children)
    return !key || n.categoryName.toLowerCase().includes(key) || n.categoryCode.toLowerCase().includes(key) || children.length ? [{...n,children}] : []
  })
  return visit(tree.value)
})
const parentOptions=computed(()=>{
  const visit=(nodes,excluded=false)=>nodes.map(n=>{
    const disabled=excluded || n.id===form.value.id
    return {value:n.id,label:n.categoryName,disabled,children:visit(n.children,disabled)}
  })
  return visit(tree.value)
})
async function load(){loading.value=true;try{tree.value=await listCategories()}finally{loading.value=false}}
function openForm(row=null,parentId=null){form.value=row?{...row}:{categoryName:'',categoryCode:'',parentId,sortNo:0,status:1};visible.value=true}
async function submit(){
  try{await formRef.value.validate()}catch{return}
  saving.value=true
  try{
    const created=!form.value.id
    const id=await saveCategory(form.value)
    visible.value=false
    ElMessage.success('分类已保存')
    await load()
    if(created && store.hasPerm('product:category:list')) await attributePanel.value.open({...form.value,id})
  }finally{saving.value=false}
}
async function onDelete(row){
  try{await ElMessageBox.confirm('确定删除分类“'+row.categoryName+'”？有下级或产品时不能删除。','删除分类',{type:'warning'})}catch{return}
  await removeCategory(row.id);ElMessage.success('分类已删除');await load()
}
onMounted(load)
</script>
