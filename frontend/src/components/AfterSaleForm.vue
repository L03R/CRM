<template>
  <el-dialog v-model="visible" :title="existing?'核实售后来源':'登记售后问题'" width="min(1050px,96vw)" append-to-body destroy-on-close>
    <el-alert title="订单暂不明确时可以先登记，后续再核实来源；这里记录客户反馈，不直接判定责任或执行退换修。" type="info" :closable="false" />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" class="case-form">
      <div class="grid">
        <el-form-item label="客户" prop="customerId"><el-select v-model="form.customerId" :disabled="fixedCustomer || existing" filterable remote :remote-method="searchCustomers" @change="customerChanged"><el-option v-for="item in customers" :key="item.id" :value="item.id" :label="item.companyName" /></el-select></el-form-item>
        <el-form-item label="关联订单"><el-select v-model="form.source.orderId" :disabled="!form.customerId" clearable filterable placeholder="不明确可暂不选择" @change="orderChanged"><el-option v-for="item in options.orders" :key="item.id" :value="item.id" :label="item.orderNo" /></el-select></el-form-item>
        <el-form-item label="发货批次"><el-select v-model="form.source.shipmentId" :disabled="!form.source.orderId" clearable @change="shipmentChanged"><el-option v-for="item in options.shipments" :key="item.id" :value="item.id" :label="item.shipmentNo" /></el-select></el-form-item>
        <el-form-item label="客户联系人"><el-select v-model="form.source.contactId" :disabled="!form.customerId" clearable @change="contactChanged"><el-option v-for="item in options.contacts" :key="item.id" :value="item.id" :label="item.contactName" /></el-select></el-form-item>
        <el-form-item label="序列号 / 批次"><el-input v-model="form.source.serialNo" maxlength="128" /></el-form-item>
      </div>
      <template v-if="!existing">
        <el-form-item label="问题标题" prop="title"><el-input v-model.trim="form.title" maxlength="128" /></el-form-item>
        <div class="grid"><el-form-item label="问题类型" prop="issueType"><el-select v-model="form.issueType"><el-option v-for="(label,key) in afterSaleTypes" :key="key" :label="label" :value="key" /></el-select></el-form-item><el-form-item label="反馈时间" prop="reportedAt"><el-date-picker v-model="form.reportedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item><el-form-item label="反馈人" prop="reporterName"><el-input v-model.trim="form.reporterName" maxlength="64" /></el-form-item><el-form-item label="联系方式" prop="reporterContact"><el-input v-model.trim="form.reporterContact" maxlength="128" /></el-form-item></div>
        <el-form-item label="客户问题描述" prop="description"><el-input v-model="form.description" type="textarea" :rows="4" maxlength="2000" /></el-form-item>
        <el-form-item label="客户期望"><el-input v-model="form.expectation" type="textarea" maxlength="500" placeholder="例如希望维修、换货、补发或技术指导" /></el-form-item>
        <el-form-item label="下次跟进"><el-date-picker v-model="form.nextFollowAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
      </template>
      <h3>涉及产品</h3>
      <el-select v-if="form.source.orderId" v-model="selectedLine" clearable filterable placeholder="选择涉及的订单 / 发货产品" style="width:440px;max-width:100%" @change="addLine"><el-option v-for="line in options.lines" :key="line.id" :value="line.id" :label="line.productName+' / '+line.skuCode" /></el-select>
      <el-button v-else @click="form.source.items.push({orderItemId:null,productName:'',skuCode:'',specification:'',quantity:1})">添加待核实产品</el-button>
      <el-table :data="form.source.items"><el-table-column label="产品 / 型号" min-width="180"><template #default="{row}"><span v-if="row.orderItemId">{{ row.productName }}<div>{{ row.skuCode }}</div></span><el-input v-else v-model="row.productName" maxlength="128" placeholder="产品或型号，待核实" /></template></el-table-column><el-table-column label="配置 / 情况" min-width="250"><template #default="{row}"><span v-if="row.orderItemId">{{ row.specification }}</span><el-input v-else v-model="row.specification" type="textarea" maxlength="2000" /></template></el-table-column><el-table-column label="反馈数量" width="155"><template #default="{row}"><el-input-number v-model="row.quantity" :min="1" :max="1000000" :precision="0" style="width:125px" controls-position="right" /></template></el-table-column><el-table-column label="操作" width="70"><template #default="{$index}"><el-button link type="danger" @click="form.source.items.splice($index,1)">移除</el-button></template></el-table-column></el-table>
      <el-form-item v-if="existing" label="核实说明" prop="note"><el-input v-model="form.note" type="textarea" maxlength="500" /></el-form-item>
      <el-form-item v-else label="问题图片"><el-upload v-model:file-list="files" :auto-upload="false" accept="image/png,image/jpeg" :limit="8" list-type="picture"><el-button>选择图片</el-button><template #tip><div>PNG / JPEG，每张不超过5MB，最多8张。登记成功后上传。</div></template></el-upload></el-form-item>
    </el-form>
    <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">{{ existing?'保存来源核实':'登记问题' }}</el-button></template>
  </el-dialog>
</template>
<script setup>
import {computed,ref} from 'vue'
import {ElMessage} from 'element-plus'
import {afterSaleCustomers,afterSaleSources,createAfterSale,updateAfterSaleSource,uploadAfterSaleImage} from '@/api/aftersale'
import {afterSaleTypes} from '@/config/aftersale'
import {localTime} from '@/config/workflow'
const emit=defineEmits(['saved'])
const visible=ref(false),saving=ref(false),existing=ref(false),fixedCustomer=ref(false),formRef=ref(),form=ref({source:{items:[]}}),customers=ref([]),options=ref({orders:[],shipments:[],contacts:[],lines:[]}),selectedLine=ref(null),files=ref([])
const rules=computed(()=>Object.fromEntries((existing.value?['note']:['customerId','title','issueType','description','reporterName','reporterContact','reportedAt']).map(key=>[key,[{required:true,message:'请填写或选择此项'}]])))
async function searchCustomers(keyword='') {customers.value=await afterSaleCustomers(keyword)}
async function loadSources() {if(!form.value.customerId)return;options.value=await afterSaleSources({customerId:form.value.customerId,orderId:form.value.source.orderId,shipmentId:form.value.source.shipmentId,caseId:existing.value?form.value.id:undefined})}
async function customerChanged() {form.value.reporterName='';form.value.reporterContact='';form.value.source={orderId:null,shipmentId:null,contactId:null,serialNo:'',items:[]};await loadSources()}
async function orderChanged() {form.value.source.shipmentId=null;form.value.source.items=[];await loadSources()}
async function shipmentChanged() {form.value.source.items=[];await loadSources()}
function contactChanged(id) {if(existing.value)return;const contact=options.value.contacts.find(item=>item.id===id);if(contact){form.value.reporterName=contact.contactName;form.value.reporterContact=contact.phone || contact.landline || contact.wechat || contact.email || ''}}
function addLine(id) {const line=options.value.lines.find(item=>item.id===id);if(!line)return;if(form.value.source.items.some(item=>item.orderItemId===id)){ElMessage.warning('该产品已选择');return}if(form.value.source.items.length>=100){ElMessage.warning('最多100行');return}form.value.source.items.push({orderItemId:id,productName:line.productName,skuCode:line.skuCode,specification:line.specification,quantity:1});selectedLine.value=null}
async function open(context={},record=null) {
  existing.value=!!record;fixedCustomer.value=!!context.customerId;files.value=[];selectedLine.value=null
  if(record) {form.value={id:record.id,version:record.version,customerId:record.customerId,note:'',source:{contactId:record.contactId,orderId:record.orderId,shipmentId:record.shipmentId,serialNo:record.serialNo,items:record.items.map(item=>({...item}))}};customers.value=[{id:record.customerId,companyName:record.customerName}]}
  else {form.value={customerId:context.customerId || null,title:'',issueType:'QUALITY',description:'',expectation:'',reporterName:'',reporterContact:'',reportedAt:localTime(),nextFollowAt:null,source:{contactId:null,orderId:context.orderId || null,shipmentId:context.shipmentId || null,serialNo:'',items:[]}};customers.value=context.customerId?[{id:context.customerId,companyName:context.customerName}]:await afterSaleCustomers()}
  options.value={orders:[],shipments:[],contacts:[],lines:[]}
  if(form.value.customerId)await loadSources()
  if(record?.orderId && !options.value.orders.some(item=>item.id===record.orderId))options.value.orders.push({id:record.orderId,orderNo:record.orderNo})
  if(record?.shipmentId && !options.value.shipments.some(item=>item.id===record.shipmentId))options.value.shipments.push({id:record.shipmentId,shipmentNo:record.shipmentNo+'（历史关联）'})
  visible.value=true
}
async function save() {
  try {await formRef.value.validate()}catch{return}
  if(form.value.source.items.some(item=>!item.orderItemId && !item.productName?.trim())){ElMessage.warning('请填写待核实产品名称');return}
  if(files.value.some(file=>file.raw.size>5*1024*1024)){ElMessage.warning('每张图片不能超过5MB');return}
  saving.value=true
  try {
    if(existing.value){await updateAfterSaleSource(form.value.id,{version:form.value.version,source:form.value.source,note:form.value.note});visible.value=false;ElMessage.success('来源核实已保存');emit('saved',form.value.id);return}
    const id=await createAfterSale(form.value)
    let failed=false
    try {for(const file of files.value)await uploadAfterSaleImage(id,file.raw)}catch{failed=true}
    visible.value=false
    if(failed)ElMessage.warning('售后问题已登记，部分图片上传失败，可在详情中继续补传');else ElMessage.success('售后问题已登记')
    emit('saved',id)
  } finally {saving.value=false}
}
defineExpose({open})
</script>
<style scoped>.case-form{margin-top:20px}.grid{display:grid;grid-template-columns:1fr 1fr;gap:0 18px}.grid :deep(.el-select),.grid :deep(.el-date-editor){width:100%}h3{margin-top:20px}@media(max-width:800px){.grid{grid-template-columns:1fr}}</style>
