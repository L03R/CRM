<template>
  <el-dialog v-model="visible" :title="'分配/转交 · '+(target.companyName||'')" width="500px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="负责人" prop="ownerId"><el-select v-model="form.ownerId" filterable style="width:100%"><el-option v-for="owner in owners" :key="owner.id" :value="owner.id" :label="owner.realName+'（'+owner.employeeNo+'）'" /></el-select></el-form-item>
      <el-form-item label="分配原因" prop="reason"><el-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" /></el-form-item>
    </el-form>
    <p class="text-sub">只显示有权分配范围内的有效销售负责人。创建人和历史记录保留。</p>
    <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
  </el-dialog>
</template>
<script setup>
import {ref} from 'vue'
import {ElMessage} from 'element-plus'
import {workflowOwners,assignWorkflowOwner} from '@/api/workflow'
const emit=defineEmits(['saved'])
const visible=ref(false), kind=ref(''), target=ref({}), owners=ref([]), form=ref({}), formRef=ref(), saving=ref(false)
const rules={ownerId:[{required:true,message:'请选择负责人'}],reason:[{required:true,message:'请填写分配或转交原因'}]}
async function open(type,row){kind.value=type;target.value=row;owners.value=await workflowOwners(type);form.value={ownerId:row.ownerId,reason:''};visible.value=true}
async function submit(){try{await formRef.value.validate()}catch{return}saving.value=true;try{await assignWorkflowOwner(kind.value,target.value.id,form.value);visible.value=false;ElMessage.success('负责人已更新');emit('saved')}finally{saving.value=false}}
defineExpose({open})
</script>
