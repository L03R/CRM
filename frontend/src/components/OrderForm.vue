<template>
  <el-dialog v-model="visible" :title="changing?'变更已确认订单':'编辑订单草稿'" width="min(1150px,96vw)" destroy-on-close append-to-body>
    <el-alert :title="changing?'变更需要经理权限、变更原因和客户确认依据，保存后保留修改前后的完整内容。':'可调整产品、数量、价格和交付要求；确认订单前请取得客户对当前内容的正式确认。'" type="info" :closable="false" />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" class="order-form">
      <div class="form-grid">
        <el-form-item label="客户采购单号"><el-input v-model.trim="form.customerOrderNo" maxlength="64" /></el-form-item>
        <el-form-item label="金额口径" prop="taxMode"><el-select v-model="form.taxMode"><el-option value="INCLUDED" label="含税价格" /><el-option value="EXCLUDED" label="未税价格" /></el-select></el-form-item>
        <el-form-item label="收货人" prop="recipientName"><el-input v-model.trim="form.recipientName" maxlength="64" /></el-form-item>
        <el-form-item label="收货电话" prop="recipientPhone"><el-input v-model.trim="form.recipientPhone" maxlength="32" /></el-form-item>
        <el-form-item label="客户要求交期"><el-date-picker v-model="form.requestedDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="公司承诺交期" prop="promisedDate"><el-date-picker v-model="form.promisedDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
      </div>
      <el-form-item label="收货地址" prop="shippingAddress"><el-input v-model.trim="form.shippingAddress" maxlength="255" /></el-form-item>
      <h3>订单产品（人民币）</h3>
      <el-select v-model="selectedSku" filterable remote :remote-method="loadSkus" :loading="skuLoading" placeholder="搜索产品名称、型号或SKU编码，添加产品" class="sku-search" @change="addSku"><el-option v-for="sku in skus" :key="sku.id" :value="sku.id" :label="sku.productName+' / '+sku.skuCode" /></el-select>
      <el-table :data="form.items" border>
        <el-table-column label="产品 / SKU" min-width="220"><template #default="{row}"><strong>{{ row.productName }}</strong><div>{{ row.modelCode }} / {{ row.skuCode }}</div><small>{{ row.specification }}</small></template></el-table-column>
        <el-table-column label="数量" width="150"><template #default="{row}"><el-input-number v-model="row.quantity" :min="1" :max="1000000" :precision="0" controls-position="right" style="width:125px" /></template></el-table-column>
        <el-table-column label="单价" width="175"><template #default="{row}"><el-input-number v-model="row.unitPrice" :min="0" :max="99999999.99" :precision="2" controls-position="right" style="width:150px" /></template></el-table-column>
        <el-table-column label="金额" width="125"><template #default="{row}">{{ money(row.quantity*row.unitPrice) }}</template></el-table-column>
        <el-table-column label="定制 / 包装要求" min-width="180"><template #default="{row}"><el-input v-model="row.requirements" type="textarea" maxlength="500" /></template></el-table-column>
        <el-table-column label="操作" width="65"><template #default="{$index}"><el-button link type="danger" @click="form.items.splice($index,1)">移除</el-button></template></el-table-column>
      </el-table>
      <p class="amount">合计：¥ {{ money(total) }}</p>
      <el-form-item label="付款条件" prop="paymentTerms"><el-input v-model="form.paymentTerms" type="textarea" maxlength="500" /></el-form-item>
      <el-form-item label="交期约定" prop="deliveryTerms"><el-input v-model="form.deliveryTerms" type="textarea" maxlength="500" /></el-form-item>
      <el-form-item label="运费约定"><el-input v-model="form.freightTerms" maxlength="255" /></el-form-item>
      <el-form-item label="包装 / 交付备注"><el-input v-model="form.remark" type="textarea" maxlength="1000" /></el-form-item>
      <template v-if="changing">
        <el-form-item label="变更原因" prop="reason"><el-input v-model="form.reason" type="textarea" maxlength="500" /></el-form-item>
        <el-form-item label="客户确认依据" prop="customerConfirmation"><el-input v-model="form.customerConfirmation" type="textarea" maxlength="500" placeholder="记录客户同意本次变更的采购单、邮件或沟通确认依据" /></el-form-item>
      </template>
      <el-form-item v-else label="正式下单依据" prop="confirmationNote" :required="checkingConfirmation"><el-input v-model="form.confirmationNote" type="textarea" maxlength="500" placeholder="确认订单前必填，例如客户采购单编号、合同编号或客户确认采购的邮件/沟通记录；草稿可暂不填写" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="visible=false">取消</el-button><el-button :loading="saving" type="primary" @click="save">{{ changing?'保存订单变更':'保存草稿' }}</el-button></template>
  </el-dialog>
</template>
<script setup>
import {computed,nextTick,ref} from 'vue'
import {ElMessage} from 'element-plus'
import {orderSkus,updateOrder,changeOrder} from '@/api/order'
import {money} from '@/config/quotation'

const emit=defineEmits(['saved'])
const visible=ref(false),saving=ref(false),changing=ref(false),checkingConfirmation=ref(false),formRef=ref(),form=ref({items:[]})
const skus=ref([]),selectedSku=ref(null),skuLoading=ref(false)
const rules=computed(()=>Object.fromEntries((changing.value?['recipientName','recipientPhone','shippingAddress','promisedDate','paymentTerms','deliveryTerms','reason','customerConfirmation']:checkingConfirmation.value?['recipientName','recipientPhone','shippingAddress','promisedDate','paymentTerms','deliveryTerms','confirmationNote']:[]).map(key=>[key,[{required:true,whitespace:true,message:'请填写此项'}]])))
const total=computed(()=>form.value.items.reduce((sum,item)=>sum+Number(item.quantity || 0)*Number(item.unitPrice || 0),0))
const editableFields=['version','customerOrderNo','recipientName','recipientPhone','shippingAddress','requestedDate','promisedDate','confirmationNote','taxMode','paymentTerms','deliveryTerms','freightTerms','remark']

async function open(order,change=false,forConfirmation=false) {
  checkingConfirmation.value=forConfirmation
  changing.value=change
  form.value={...order,items:order.items.map(item=>({...item})),reason:'',customerConfirmation:''}
  selectedSku.value=null
  visible.value=true
  await loadSkus()
  if(forConfirmation) {await nextTick();await formRef.value.validate().catch(()=>{})}
}
async function loadSkus(keyword='') {
  skuLoading.value=true
  try {skus.value=await orderSkus(form.value.id,keyword)} finally {skuLoading.value=false}
}
function addSku(id) {
  if(form.value.items.length>=100) {ElMessage.warning('每张订单最多100行');return}
  const sku=skus.value.find(item=>item.id===id)
  form.value.items.push({...sku,id:undefined,skuId:sku.id,quantity:1,unitPrice:sku.salePrice ?? 0,requirements:''})
  selectedSku.value=null
}
async function save() {
  try {await formRef.value.validate()} catch {return}
  if(!form.value.items.length) {ElMessage.warning('请至少保留一项产品');return}
  saving.value=true
  try {
    const data=Object.fromEntries(editableFields.map(key=>[key,form.value[key]]))
    data.items=form.value.items.map(({id,skuId,quantity,unitPrice,requirements})=>({id,skuId,quantity,unitPrice,requirements}))
    if(changing.value) await changeOrder(form.value.id,{content:data,reason:form.value.reason,customerConfirmation:form.value.customerConfirmation})
    else await updateOrder(form.value.id,data)
    visible.value=false
    ElMessage.success(changing.value?'订单变更已保存，历史内容已保留':'订单草稿已保存')
    emit('saved',form.value.id)
  } finally {saving.value=false}
}
defineExpose({open})
</script>
<style scoped>.order-form{margin-top:20px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 20px}.form-grid :deep(.el-select),.form-grid :deep(.el-date-editor){width:100%}.sku-search{width:440px;max-width:100%;margin-bottom:16px}.amount{text-align:right;font-weight:600}small{color:#758196;white-space:pre-wrap}@media(max-width:800px){.form-grid{grid-template-columns:1fr}}</style>
