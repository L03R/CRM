<template>
  <el-dialog v-model="visible" :title="form.id?'编辑商机':'建立商机'" width="560px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="客户" prop="customerId"><el-select v-model="form.customerId" :disabled="!!form.id || fixedCustomer" filterable remote :remote-method="loadCustomers" :loading="loading" style="width:100%" placeholder="输入客户名称搜索"><el-option v-for="customer in customers" :key="customer.id" :value="customer.id" :label="customer.companyName" /></el-select></el-form-item>
      <el-form-item label="商机名称" prop="title"><el-input v-model.trim="form.title" maxlength="128" /></el-form-item>
      <el-form-item label="预计金额"><el-input-number v-model="form.expectedAmount" :min="0" :max="9999999999.99" :precision="2" /></el-form-item>
      <el-form-item label="预计成交"><el-date-picker v-model="form.expectedCloseDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
      <el-form-item label="阶段" prop="stage"><el-select v-model="form.stage"><el-option v-for="(name,key) in opportunityStages" :key="key" :value="key" :label="name" /></el-select></el-form-item>
      <el-form-item v-if="['WON','LOST'].includes(form.stage)" label="结果说明" prop="closeReason" :rules="[{required:true,message:'请填写结束原因或结果'}]"><el-input v-model="form.closeReason" type="textarea" maxlength="500" /></el-form-item>
      <el-form-item label="采购需求"><el-input v-model="form.remark" type="textarea" :rows="4" maxlength="2000" /></el-form-item>
    </el-form>
    <p class="text-sub">负责人跟随客户归属；有未结束商机的客户不能进入公海。商机记录不代表报价或销售订单已经生成。</p>
    <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存商机</el-button></template>
  </el-dialog>
</template>
<script setup>
import {ref} from 'vue'
import {ElMessage} from 'element-plus'
import {opportunityCustomers,saveOpportunity} from '@/api/workflow'
import {opportunityStages} from '@/config/workflow'
const emit=defineEmits(['saved'])
const visible=ref(false), formRef=ref(), form=ref({}), customers=ref([]), fixedCustomer=ref(false), loading=ref(false), saving=ref(false)
const rules={customerId:[{required:true,message:'请选择客户'}],title:[{required:true,message:'请输入商机名称'}],stage:[{required:true,message:'请选择阶段'}]}
async function loadCustomers(keyword=''){loading.value=true;try{customers.value=(await opportunityCustomers(keyword)).records}finally{loading.value=false}}
async function open(row=null,customer=null){
  form.value=row?{...row}:{customerId:customer?.id || null,title:'',expectedAmount:null,expectedCloseDate:null,stage:'INITIAL',remark:'',closeReason:''}
  fixedCustomer.value=!!customer
  if(row || customer)customers.value=[{id:form.value.customerId,companyName:row?.customerName || customer.companyName}]
  else await loadCustomers()
  visible.value=true
}
async function submit(){try{await formRef.value.validate()}catch{return}saving.value=true;try{await saveOpportunity(form.value);visible.value=false;ElMessage.success('商机已保存');emit('saved')}finally{saving.value=false}}
defineExpose({open})
</script>
