<template>
  <el-dialog v-model="visible" title="修改登录密码" width="450px" destroy-on-close @closed="form = {}">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="原密码" prop="oldPassword"><el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" maxlength="72" /></el-form-item>
      <el-form-item label="新密码" prop="newPassword"><el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" maxlength="72" /></el-form-item>
      <el-form-item label="确认密码" prop="confirm"><el-input v-model="form.confirm" type="password" show-password autocomplete="new-password" maxlength="72" /></el-form-item>
    </el-form>
    <p class="text-sub">至少8位，包含字母和数字。保存后所有设备的旧会话失效，请重新登录。</p>
    <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存并重新登录</el-button></template>
  </el-dialog>
</template>
<script setup>
import {ref} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import request from '@/api/request'
import {useUserStore} from '@/store/user'
const router=useRouter(), store=useUserStore()
const visible=ref(false), form=ref({}), formRef=ref(), saving=ref(false)
const rules={oldPassword:[{required:true,message:'请输入原密码'}],newPassword:[{required:true,message:'请输入新密码'},{pattern:/^(?=.*[A-Za-z])(?=.*[0-9]).{8,72}$/,message:'8-72位，包含字母和数字'}],confirm:[{required:true,message:'请确认新密码'},{validator:(_rule,value,done)=>done(value===form.value.newPassword?undefined:new Error('两次密码不一致'))}]}
function open(){form.value={oldPassword:'',newPassword:'',confirm:''};visible.value=true}
async function submit(){
  try{await formRef.value.validate()}catch{return}
  saving.value=true
  try{await request.put('/auth/password',{oldPassword:form.value.oldPassword,newPassword:form.value.newPassword});visible.value=false;store.resetState();ElMessage.success('密码已修改，请重新登录');await router.replace('/login')}
  finally{saving.value=false}
}
defineExpose({open})
</script>
