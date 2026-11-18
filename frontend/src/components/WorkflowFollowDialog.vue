<template>
  <el-dialog v-model="visible" :title="'记录跟进 · '+(target.companyName||'')" width="560px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="95px">
      <el-form-item v-if="kind==='CUSTOMER' && store.hasPerm('contact:list')" label="联系对象"><el-select v-model="form.contactId" filterable remote :remote-method="loadContacts" clearable placeholder="不选时使用主要联系人" style="width:100%"><el-option v-for="contact in contacts" :key="contact.id" :value="contact.id" :label="contact.contactName+(contact.jobTitle?' · '+contact.jobTitle:'')" /></el-select></el-form-item>
      <el-form-item label="沟通方式" prop="method"><el-select v-model="form.method"><el-option v-for="(label,key) in followMethods" :key="key" :value="key" :label="label" /></el-select></el-form-item>
      <el-form-item label="沟通时间" prop="occurredAt"><el-date-picker v-model="form.occurredAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></el-form-item>
      <el-form-item label="沟通内容" prop="content"><el-input v-model="form.content" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="记录实际沟通内容、客户反馈和下一步安排" /></el-form-item>
      <el-form-item label="下次跟进"><el-date-picker v-model="form.nextFollowAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" clearable /></el-form-item>
    </el-form>
    <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存跟进</el-button></template>
  </el-dialog>
</template>
<script setup>
import {ref} from 'vue'
import {useUserStore} from '@/store/user'
import {pageContacts} from '@/api/contact'
import {ElMessage} from 'element-plus'
import {addWorkflowFollow} from '@/api/workflow'
import {followMethods,localTime} from '@/config/workflow'
const emit=defineEmits(['saved'])
const store=useUserStore(),contacts=ref([])
const visible=ref(false), kind=ref(''), target=ref({}), form=ref({}), formRef=ref(), saving=ref(false)
const rules={method:[{required:true,message:'请选择沟通方式'}],occurredAt:[{required:true,message:'请选择沟通时间'}],content:[{required:true,message:'请填写实际沟通内容'}]}
async function loadContacts(keyword=''){contacts.value=(await pageContacts({customerId:target.value.id,status:1,keyword,size:50})).records}
async function open(type,row){kind.value=type;target.value=row;contacts.value=[];form.value={method:'PHONE',occurredAt:localTime(),content:'',nextFollowAt:null,contactId:null};if(type==='CUSTOMER' && store.hasPerm('contact:list'))await loadContacts();visible.value=true}
async function submit(){try{await formRef.value.validate()}catch{return}saving.value=true;try{await addWorkflowFollow(kind.value,target.value.id,form.value);visible.value=false;ElMessage.success('跟进已保存');emit('saved')}finally{saving.value=false}}
defineExpose({open})
</script>
