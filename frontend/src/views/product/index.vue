<template>
  <div class="page">
    <div class="page-header"><div><h2 class="page-title">产品档案</h2><p class="page-desc">{{brandName}} · 型号、规格、供货方式与销售SKU</p></div><el-button v-if="store.hasPerm('product:add')" type="primary" @click="openForm()">新增产品</el-button></div>
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="产品名称 / 型号" clearable style="width:230px" @keyup.enter="load(true)" />
        <el-tree-select v-model="query.categoryId" :data="filterOptions" check-strictly clearable placeholder="分类（含下级）" style="width:180px" />
        <el-select v-model="query.supplyType" placeholder="供货方式" clearable style="width:130px"><el-option v-for="(label,key) in supplyTypes" :key="key" :label="label" :value="Number(key)" /></el-select>
        <el-select v-model="query.status" placeholder="上下架" clearable style="width:120px"><el-option label="上架" :value="1" /><el-option label="下架" :value="0" /></el-select>
        <el-button type="primary" @click="load(true)">查询</el-button><el-button @click="reset">重置</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column prop="modelCode" label="型号" width="160" />
        <el-table-column prop="productName" label="产品名称" min-width="190" />
        <el-table-column prop="categoryName" label="分类" width="140" />
        <el-table-column label="供货方式" width="95"><template #default="{row}">{{supplyTypes[row.supplyType]}}</template></el-table-column>
        <el-table-column prop="unit" label="单位" width="65" />
        <el-table-column label="零售参考价" width="120"><template #default="{row}">{{money(row.retailPrice)}}</template></el-table-column>
        <el-table-column prop="skuCount" label="SKU数" width="80" />
        <el-table-column label="状态" width="135"><template #default="{row}"><el-tag :type="row.available?'success':'info'">{{row.status===0?'下架':row.available?'上架':'分类停用'}}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="205" fixed="right"><template #default="{row}">
          <el-button link type="primary" @click="openDetail(row.id)">详情/SKU</el-button>
          <el-button v-if="store.hasPerm('product:edit')" link type="primary" @click="openForm(row)">编辑</el-button>
          <el-button v-if="store.hasPerm('product:status')" link type="warning" @click="onStatus(row)">{{row.status===1?'下架':'上架'}}</el-button>
        </template></el-table-column>
      </el-table>
      <p class="text-sub">价格、交期为空表示待维护，并非免费或现货承诺；上架表示目录可展示，下单需另行校验SKU和交易条件。</p>
      <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="load(true)" />
    </div>
    <el-dialog v-model="formVisible" :title="form.id?'编辑产品':'新增产品'" width="650px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="产品名称" prop="productName"><el-input v-model.trim="form.productName" maxlength="128" /></el-form-item>
        <el-form-item label="型号编码" prop="modelCode"><el-input v-model.trim="form.modelCode" :disabled="!!form.id" maxlength="64" placeholder="如 XM-A200" /></el-form-item>
        <el-form-item label="产品分类" prop="categoryId"><el-tree-select v-model="form.categoryId" :data="editOptions" :disabled="templateLoading" check-strictly style="width:100%" placeholder="选择启用的末级分类" @change="changeCategory" /></el-form-item>
        <el-form-item label="品牌" prop="brand"><el-input v-model.trim="form.brand" maxlength="64" /></el-form-item>
        <el-form-item label="系列"><el-input v-model.trim="form.series" maxlength="64" /></el-form-item>
        <el-form-item label="供货方式"><el-select v-model="form.supplyType"><el-option v-for="(label,key) in supplyTypes" :key="key" :label="label" :value="Number(key)" /></el-select></el-form-item>
        <el-form-item label="销售单位" prop="unit"><el-input v-model.trim="form.unit" maxlength="16" placeholder="把 / 台 / 个 / 套 / 包" /></el-form-item>
        <el-form-item label="零售参考价"><el-input-number v-model="form.retailPrice" :min="0" :max="9999999999.99" :precision="2" /></el-form-item>
        <el-form-item label="渠道参考价"><el-input-number v-model="form.channelPrice" :min="0" :max="9999999999.99" :precision="2" /></el-form-item>
        <el-form-item label="参考交期"><el-input-number v-model="form.leadDays" :min="0" :precision="0" /> 天</el-form-item>
        <el-form-item label="图片地址"><el-input v-model.trim="form.imageUrl" maxlength="500" placeholder="可选，图片URL" /></el-form-item>
        <el-divider content-position="left">品类技术属性</el-divider>
        <el-alert v-if="form.id" title="历史型号存在未知参数时，请按实际产品补齐；更换分类会清理不兼容的型号及SKU属性。" type="info" :closable="false" />
        <ProductAttributes v-model="form.attributes" :fields="attributeTemplate" />
        <el-form-item v-if="attributeTemplate.some(f => f.attributeCode === 'top_construction')" label="结构摘要"><el-tag>{{ constructionSummary(form.attributes) }}</el-tag></el-form-item>
        <el-form-item label="补充说明"><el-input v-model="form.specifications" type="textarea" :rows="3" maxlength="2000" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="formVisible=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="templateLoading" @click="submit">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="产品详情与SKU" size="min(900px, 95vw)">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="型号">{{detail.modelCode}}</el-descriptions-item><el-descriptions-item label="产品">{{detail.productName}}</el-descriptions-item>
          <el-descriptions-item label="品牌/系列">{{detail.brand}} / {{detail.series || '—'}}</el-descriptions-item><el-descriptions-item label="供货方式">{{supplyTypes[detail.supplyType]}}</el-descriptions-item>
          <el-descriptions-item label="参考零售价">{{money(detail.retailPrice)}}</el-descriptions-item><el-descriptions-item label="渠道参考价">{{money(detail.channelPrice)}}</el-descriptions-item>
          <el-descriptions-item label="参考交期">{{detail.leadDays==null?'待维护':detail.leadDays+'天'}}</el-descriptions-item><el-descriptions-item label="可用状态">{{detail.available?'上架且分类启用':'下架或分类停用'}}</el-descriptions-item>
          <el-descriptions-item label="补充说明" :span="2">{{detail.specifications || '—'}}</el-descriptions-item>
          <el-descriptions-item label="图片地址" :span="2">{{detail.imageUrl || '—'}}</el-descriptions-item>
        </el-descriptions>
        <h3>品类技术属性 <el-tag v-if="detail.constructionSummary">{{ detail.constructionSummary }}</el-tag></h3>
        <el-alert v-if="detail.missingAttributes.length" :title="'资料待完善：' + detail.missingAttributes.join('、')" type="warning" :closable="false" />
        <ProductAttributes :fields="detail.attributeTemplate" :model-value="detail.attributes" read-only />
        <div class="sku-header"><h3>销售SKU</h3><el-button v-if="store.hasPerm('product:add')" type="primary" @click="openSku()">新增SKU</el-button></div>
        <el-table :data="detail.skus" row-key="id">
          <el-table-column prop="skuCode" label="SKU编码" min-width="195" />
          <el-table-column v-for="field in detail.skuAttributeTemplate" :key="field.attributeCode" :label="field.attributeName" min-width="110">
            <template #default="{row}">{{ attributeText(field, row.attributes) }}</template>
          </el-table-column>
          <el-table-column label="销售参考价" width="115"><template #default="{row}">{{money(row.salePrice)}}</template></el-table-column>
          <el-table-column label="状态" width="70"><template #default="{row}">{{row.status===1?'启用':'停用'}}</template></el-table-column>
          <el-table-column label="操作" width="110"><template #default="{row}">
            <el-button v-if="store.hasPerm('product:edit')" link type="primary" @click="openSku(row)">编辑</el-button>
            <el-button v-if="store.hasPerm('product:status')" link type="warning" @click="onSkuStatus(row)">{{row.status===1?'停用':'启用'}}</el-button>
          </template></el-table-column>
        </el-table>
      </template>
    </el-drawer>
    <el-dialog v-model="skuVisible" :title="skuForm.id?'编辑SKU':'新增SKU'" width="480px" destroy-on-close>
      <el-form ref="skuFormRef" :model="skuForm" :rules="skuRules" label-width="100px">
        <el-form-item label="SKU编码" prop="skuCode"><el-input v-model.trim="skuForm.skuCode" :disabled="!!skuForm.id" maxlength="64" /></el-form-item>
        <ProductAttributes v-model="skuForm.attributes" :fields="detail?.skuAttributeTemplate || []" />
        <el-form-item label="销售参考价"><el-input-number v-model="skuForm.salePrice" :min="0" :max="9999999999.99" :precision="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="skuVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitSku">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import {computed,onMounted,reactive,ref} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import {useUserStore} from '@/store/user'
import {brandName} from '@/config/company'
import {listCategories,getCategoryAttributes,pageProducts,getProduct,saveProduct,changeProductStatus,saveSku,changeSkuStatus} from '@/api/product'
import ProductAttributes from '@/components/ProductAttributes.vue'
import {compatibleAttributes,attributeText,constructionSummary} from '@/utils/productAttributes'

const store=useUserStore()
const supplyTypes={1:'自制',2:'委外',3:'外购'}
const rows=ref([]), categories=ref([]), loading=ref(false), saving=ref(false)
const total=ref(0)
const query=reactive({keyword:'',categoryId:null,status:null,supplyType:null,page:1,size:20})
const formVisible=ref(false), formRef=ref(), form=ref({}), detailVisible=ref(false), detail=ref(null)
const skuVisible=ref(false), skuFormRef=ref(), skuForm=ref({})
const attributeTemplate=ref([]), templateLoading=ref(false), loadedCategoryId=ref(null)
const codeRule={pattern:/^[A-Z0-9_-]{2,64}$/,message:'2-64位大写字母、数字、下划线或短横线'}
const rules={productName:[{required:true,message:'请输入产品名称'}],modelCode:[{required:true,message:'请输入型号'},codeRule],categoryId:[{required:true,message:'请选择分类'}],brand:[{required:true,message:'请输入品牌'}],unit:[{required:true,message:'请输入销售单位'}]}
const skuRules={skuCode:[{required:true,message:'请输入SKU编码'},codeRule]}
const convert=(nodes,editing=false,parentDisabled=false)=>nodes.map(n=>{
  const disabled=parentDisabled || n.status!==1
  return {value:n.id,label:n.categoryName,disabled:editing && (disabled || n.children.length>0),children:convert(n.children,editing,disabled)}
})
const filterOptions=computed(()=>convert(categories.value))
const editOptions=computed(()=>convert(categories.value,true))
const money=(value)=>value==null?'未设置':'¥ '+Number(value).toFixed(2)
async function load(resetPage=false){if(resetPage===true)query.page=1;loading.value=true;try{const [result,tree]=await Promise.all([pageProducts(query),listCategories()]);rows.value=result.records;total.value=result.total;categories.value=tree}finally{loading.value=false}}
function reset(){Object.assign(query,{keyword:'',categoryId:null,status:null,supplyType:null,page:1});load()}
async function openForm(row=null){
  if(row){
    const product=await getProduct(row.id)
    form.value={...product,attributes:{...product.attributes}}
    attributeTemplate.value=product.attributeTemplate
    loadedCategoryId.value=product.categoryId
  }else{
    form.value={productName:'',modelCode:'',categoryId:null,brand:brandName,series:'',specifications:'',imageUrl:'',supplyType:1,unit:'把',retailPrice:null,channelPrice:null,leadDays:null,attributes:{}}
    attributeTemplate.value=[]
    loadedCategoryId.value=null
  }
  formVisible.value=true
}
async function changeCategory(categoryId){
  const previous=loadedCategoryId.value
  if(form.value.id && previous!==categoryId){
    try{await ElMessageBox.confirm('更换品类会清理不兼容的型号和SKU参数，并要求补齐新属性。是否继续？','更换品类',{type:'warning'})}
    catch{form.value.categoryId=previous;return}
  }
  templateLoading.value=true
  try{
    const template=await getCategoryAttributes(categoryId)
    if(form.value.categoryId!==categoryId)return
    attributeTemplate.value=template.model
    form.value.attributes=compatibleAttributes(template.model,form.value.attributes)
    loadedCategoryId.value=categoryId
    formRef.value?.clearValidate()
  }catch(error){form.value.categoryId=previous;throw error}
  finally{templateLoading.value=false}
}
async function submit(){
  try{await formRef.value.validate()}catch{return}
  saving.value=true
  try{await saveProduct(form.value);formVisible.value=false;ElMessage.success('产品已保存');await load()}finally{saving.value=false}
}
async function openDetail(id){detail.value=await getProduct(id);detailVisible.value=true}
async function onStatus(row){
  try{await ElMessageBox.confirm('确定'+(row.status===1?'下架':'上架')+'“'+row.productName+'”？','产品状态',{type:'warning'})}catch{return}
  await changeProductStatus(row.id,1-row.status);await load()
}
function openSku(row=null){skuForm.value=row?{...row,attributes:{...row.attributes}}:{skuCode:detail.value.modelCode+'-',salePrice:null,attributes:{}};skuVisible.value=true}
async function submitSku(){
  try{await skuFormRef.value.validate()}catch{return}
  saving.value=true
  try{await saveSku(detail.value.id,skuForm.value);skuVisible.value=false;ElMessage.success('SKU已保存');await openDetail(detail.value.id);await load()}finally{saving.value=false}
}
async function onSkuStatus(row){
  try{await ElMessageBox.confirm('确定'+(row.status===1?'停用':'启用')+'该SKU？','SKU状态',{type:'warning'})}catch{return}
  await changeSkuStatus(detail.value.id,row.id,1-row.status);await openDetail(detail.value.id)
}
onMounted(load)
</script>

<style scoped>
.sku-header{display:flex;align-items:center;justify-content:space-between;margin:20px 0 10px}
</style>
