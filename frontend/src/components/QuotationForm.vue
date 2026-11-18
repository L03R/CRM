<template>
  <el-dialog v-model="visible" :title="form.id?'编辑报价草稿':'新建报价'" width="min(1150px,96vw)" destroy-on-close append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <div class="form-grid">
        <el-form-item label="客户" prop="customerId"><el-select v-model="form.customerId" :disabled="fixedCustomer || !!form.id" filterable remote :remote-method="loadCustomers" placeholder="输入客户名称搜索" @change="customerChanged"><el-option v-for="item in customers" :key="item.id" :label="item.companyName" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="关联商机" prop="opportunityId"><el-select v-model="form.opportunityId" :disabled="fixedOpportunity || !!form.id || !form.customerId" filterable><el-option v-for="item in opportunities" :key="item.id" :label="item.title" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="报价标题" prop="title"><el-input v-model.trim="form.title" maxlength="128" /></el-form-item>
        <el-form-item label="客户联系人" prop="contactId"><el-select v-model="form.contactId" :disabled="!form.customerId" filterable remote :remote-method="loadContacts"><el-option v-for="item in contacts" :key="item.id" :label="item.contactName+(item.jobTitle?' · '+item.jobTitle:'')" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="报价日期" prop="quoteDate"><el-date-picker v-model="form.quoteDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="有效期至" prop="validUntil"><el-date-picker v-model="form.validUntil" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="金额口径" prop="taxMode"><el-select v-model="form.taxMode"><el-option value="INCLUDED" label="含税价格" /><el-option value="EXCLUDED" label="未税价格" /></el-select></el-form-item>
        <el-form-item label="运费约定"><el-input v-model="form.freightTerms" maxlength="255" placeholder="例如运费另计，或由卖方承担" /></el-form-item>
      </div>
      <el-alert v-if="form.customerId && !opportunities.length" title="该客户暂无可报价的进行中商机，请先在商机管理中建立商机。" type="info" :closable="false" />
      <h3>报价明细（人民币）</h3>
      <div class="sku-search"><el-select v-model="selectedSku" filterable remote :remote-method="loadSkus" :loading="skuLoading" placeholder="搜索产品名称、型号或SKU编码" style="width:440px" @change="addSku"><el-option v-for="sku in skus" :key="sku.id" :value="sku.id" :label="sku.productName+' / '+sku.skuCode" /></el-select><span class="text-sub">选择具体SKU，规格在保存时留存快照。</span></div>
      <el-table :data="form.items" border>
        <el-table-column label="产品 / SKU" min-width="210"><template #default="{row}"><strong>{{ row.productName }}</strong><div>{{ row.modelCode }} / {{ row.skuCode }}</div><p class="specification">{{ row.specification || '规格待补充' }}</p></template></el-table-column>
        <el-table-column label="数量" width="150"><template #default="{row}"><el-input-number v-model="row.quantity" :min="1" :max="1000000" :precision="0" controls-position="right" style="width:125px" /> {{ row.unit }}</template></el-table-column>
        <el-table-column label="单价（元）" width="180"><template #default="{row}"><el-input-number v-model="row.unitPrice" :min="0" :max="99999999.99" :precision="2" controls-position="right" style="width:155px" /></template></el-table-column>
        <el-table-column label="小计（元）" width="150"><template #default="{row}">{{ money(row.quantity*row.unitPrice) }}</template></el-table-column>
        <el-table-column label="定制 / 包装要求" min-width="180"><template #default="{row}"><el-input v-model="row.requirements" type="textarea" :rows="2" maxlength="500" /></template></el-table-column>
        <el-table-column label="操作" width="65"><template #default="{$index}"><el-button link type="danger" @click="form.items.splice($index,1)">移除</el-button></template></el-table-column>
      </el-table>
      <p class="amount">合计：¥ {{ money(total) }} <small>预览金额，最终以服务端计算为准</small></p>
      <el-form-item label="付款条件" prop="paymentTerms"><el-input v-model="form.paymentTerms" type="textarea" :rows="2" maxlength="500" /></el-form-item>
      <el-form-item label="交期约定" prop="deliveryTerms"><el-input v-model="form.deliveryTerms" type="textarea" :rows="2" maxlength="500" /></el-form-item>
      <el-form-item label="对外备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" placeholder="将显示在客户报价单上" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存草稿</el-button></template>
  </el-dialog>
</template>
<script setup>
import {computed,ref} from 'vue'
import {ElMessage} from 'element-plus'
import {getQuotation,saveQuotation,quotationCustomers,quotationOpportunities,quotationContacts,quotationSkus} from '@/api/quotation'
import {localTime} from '@/config/workflow'
import {money} from '@/config/quotation'
const emit=defineEmits(['saved'])
const visible=ref(false),saving=ref(false),formRef=ref(),form=ref({items:[]}),customers=ref([]),opportunities=ref([]),contacts=ref([]),skus=ref([]),selectedSku=ref(null),skuLoading=ref(false),fixedCustomer=ref(false),fixedOpportunity=ref(false)
const rules=Object.fromEntries(['customerId','opportunityId','contactId','title','quoteDate','validUntil','taxMode','paymentTerms','deliveryTerms'].map(key=>[key,[{required:true,message:'请填写或选择此项'}]]))
const total=computed(()=>form.value.items.reduce((sum,row)=>sum+Number(row.quantity || 0)*Number(row.unitPrice || 0),0))
async function loadCustomers(keyword=''){customers.value=await quotationCustomers(keyword)}
async function loadContacts(keyword=''){contacts.value=await quotationContacts({customerId:form.value.customerId,keyword,mode:form.value.id?'EDIT':'CREATE'})}
async function customerChanged(){form.value.opportunityId=null;form.value.contactId=null;const [items]=await Promise.all([quotationOpportunities({customerId:form.value.customerId}),loadContacts()]);opportunities.value=items;form.value.contactId=contacts.value.find(c=>c.isPrimary)?.id || null}
async function loadSkus(keyword=''){skuLoading.value=true;try{skus.value=await quotationSkus(keyword)}finally{skuLoading.value=false}}
function addSku(id){const sku=skus.value.find(s=>s.id===id);if(!sku)return;if(form.value.items.length>=100){ElMessage.warning('每份报价最多100行');return}form.value.items.push({skuId:sku.id,...sku,id:undefined,quantity:1,unitPrice:sku.salePrice ?? 0,requirements:''});selectedSku.value=null}
async function open(id=null,context={}){
  fixedCustomer.value=!!context.customer || !!context.opportunity;fixedOpportunity.value=!!context.opportunity;selectedSku.value=null
  if(id){form.value=await getQuotation(id);customers.value=[{id:form.value.customerId,companyName:form.value.currentCustomerName}];opportunities.value=[{id:form.value.opportunityId,title:form.value.opportunityTitle}];await loadContacts();if(!contacts.value.some(c=>c.id===form.value.contactId))form.value.contactId=null}
  else{
    const today=localTime().slice(0,10),until=localTime(new Date(Date.now()+14*86400000)).slice(0,10)
    form.value={customerId:context.customer?.id || context.opportunity?.customerId || null,opportunityId:context.opportunity?.id || null,contactId:null,title:context.opportunity?context.opportunity.title+'报价':'',quoteDate:today,validUntil:until,taxMode:'INCLUDED',paymentTerms:'',deliveryTerms:'',freightTerms:'',remark:'',items:[]}
    if(form.value.customerId){customers.value=[{id:form.value.customerId,companyName:context.customer?.companyName || context.opportunity.customerName}];await loadContacts();form.value.contactId=contacts.value.find(c=>c.isPrimary)?.id || null;opportunities.value=context.opportunity?[context.opportunity]:await quotationOpportunities({customerId:form.value.customerId})}else{opportunities.value=[];contacts.value=[];await loadCustomers()}
  }
  await loadSkus();visible.value=true
}
async function submit(){try{await formRef.value.validate()}catch{return}if(!form.value.items.length){ElMessage.warning('请添加至少一项产品SKU');return}if(form.value.items.some(row=>!Number.isInteger(row.quantity) || row.quantity<1 || row.unitPrice==null)){ElMessage.warning('请完整填写每一行的数量和单价');return}saving.value=true;try{await saveQuotation(form.value);visible.value=false;ElMessage.success('报价草稿已保存');emit('saved')}finally{saving.value=false}}
defineExpose({open})
</script>
<style scoped>.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 20px}.form-grid :deep(.el-select),.form-grid :deep(.el-date-editor){width:100%}.sku-search{display:flex;gap:15px;align-items:center;margin:16px 0}.specification{font-size:12px;color:#758196;white-space:pre-wrap}.amount{text-align:right;font-weight:600}.amount small{font-weight:400;color:#758196;margin-left:12px}@media(max-width:800px){.form-grid{grid-template-columns:1fr}.sku-search{flex-wrap:wrap}}</style>
