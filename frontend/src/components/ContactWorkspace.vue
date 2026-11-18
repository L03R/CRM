<template>
  <div class="card contact-card">
    <div class="contact-header"><h3>{{ customer?'客户联系人':'联系人列表' }}</h3><el-button v-if="canCreate" type="primary" @click="open()">新增联系人</el-button></div>
    <div class="toolbar"><el-input v-model="query.keyword" placeholder="姓名 / 客户 / 电话 / 微信 / 职务" clearable style="width:260px" @keyup.enter="load(true)" /><el-select v-model="query.status" clearable placeholder="联系人状态" style="width:140px" @change="load(true)"><el-option label="有效" :value="1" /><el-option label="离职 / 停用" :value="0" /></el-select><el-button @click="load(true)">查询</el-button></div>
    <el-table v-loading="loading" :data="rows" row-key="id">
      <el-table-column prop="contactName" label="姓名" min-width="110" />
      <el-table-column v-if="!customer" prop="companyName" label="所属客户" min-width="220" />
      <el-table-column prop="jobTitle" label="职务 / 职责" min-width="120" />
      <el-table-column prop="phone" label="手机" min-width="140" />
      <el-table-column prop="landline" label="座机" min-width="140" />
      <el-table-column prop="wechat" label="微信" min-width="140" />
      <el-table-column prop="email" label="邮箱" min-width="200" />
      <el-table-column label="标记" width="110"><template #default="{row}"><el-tag v-if="row.isPrimary" type="success">主要联系人</el-tag><el-tag v-if="row.status===0" type="info">离职 / 停用</el-tag></template></el-table-column>
      <el-table-column label="操作" width="245" fixed="right"><template #default="{row}"><el-button link type="primary" @click="open(row,true)">详情</el-button><el-button v-if="row.actions.includes('edit')" link type="primary" @click="open(row)">编辑</el-button><el-button v-if="row.actions.includes('primary') && row.status===1 && !row.isPrimary" link type="primary" @click="primary(row)">设为主要</el-button><el-button v-if="row.actions.includes('status')" link :type="row.status===1?'warning':'success'" @click="status(row)">{{ row.status===1?'停用':'启用' }}</el-button></template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="load(true)" />
    <el-dialog v-model="visible" :title="readonly?'联系人详情':form.id?'编辑联系人':'新增联系人'" width="580px" destroy-on-close append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="readonly">
        <el-form-item label="所属客户" prop="customerId"><el-select v-model="form.customerId" :disabled="!!form.id || !!customer" filterable remote :remote-method="loadCustomers" :loading="loadingCustomers" placeholder="输入客户名称搜索" style="width:100%"><el-option v-for="item in customers" :key="item.id" :value="item.id" :label="item.companyName" /></el-select></el-form-item>
        <el-form-item label="姓名" prop="contactName"><el-input v-model.trim="form.contactName" maxlength="64" /></el-form-item>
        <el-form-item label="职务 / 职责"><el-input v-model.trim="form.jobTitle" maxlength="64" placeholder="采购经理 / 财务 / 收货人" /></el-form-item>
        <el-form-item label="手机"><el-input v-model.trim="form.phone" maxlength="32" /></el-form-item>
        <el-form-item label="座机"><el-input v-model.trim="form.landline" maxlength="32" /></el-form-item>
        <el-form-item label="微信"><el-input v-model.trim="form.wechat" maxlength="64" /></el-form-item>
        <el-form-item label="邮箱" prop="email"><el-input v-model.trim="form.email" maxlength="128" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="form.status" :disabled="!!form.id && !form.actions?.includes('status')"><el-option label="有效" :value="1" /><el-option label="离职 / 停用" :value="0" /></el-select></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="1000" /></el-form-item>
      </el-form>
      <p class="text-sub">至少填写一种联系方式；首个有效联系人自动设为主要联系人。停用主要联系人时自动选择其他有效联系人。</p>
      <template #footer><el-button @click="visible=false">{{ readonly?'关闭':'取消' }}</el-button><el-button v-if="!readonly" type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup>
import {computed,onMounted,reactive,ref} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import {useUserStore} from '@/store/user'
import {pageContacts,contactCustomers,saveContact,setPrimaryContact,setContactStatus} from '@/api/contact'
const props=defineProps({customer:Object}),emit=defineEmits(['changed']),store=useUserStore()
const query=reactive({keyword:'',status:null,page:1,size:20}),rows=ref([]),total=ref(0),loading=ref(false)
const form=ref({}),formRef=ref(),visible=ref(false),readonly=ref(false),saving=ref(false),customers=ref([]),loadingCustomers=ref(false)
const canCreate=computed(()=>props.customer?props.customer.actions.includes('contactAdd'):store.hasPerm('contact:add'))
const rules={customerId:[{required:true,message:'请选择客户'}],contactName:[{required:true,message:'请输入姓名'}],email:[{type:'email',message:'邮箱格式不正确'}]}
async function load(reset=false){if(reset===true)query.page=1;loading.value=true;try{const result=await pageContacts({...query,customerId:props.customer?.id});rows.value=result.records;total.value=result.total}finally{loading.value=false}}
async function loadCustomers(keyword=''){loadingCustomers.value=true;try{customers.value=await contactCustomers(keyword)}finally{loadingCustomers.value=false}}
async function open(row=null,view=false){readonly.value=view;form.value=row?{...row}:{customerId:props.customer?.id,contactName:'',jobTitle:'',phone:'',landline:'',wechat:'',email:'',status:1,remark:''};if(row || props.customer)customers.value=[{id:form.value.customerId,companyName:row?.companyName || props.customer.companyName}];else await loadCustomers();visible.value=true}
async function changed(){await load();emit('changed')}
async function submit(){try{await formRef.value.validate()}catch{return}if(![form.value.phone,form.value.landline,form.value.wechat,form.value.email].some(v=>v?.trim())){ElMessage.warning('请至少填写一种联系方式');return}saving.value=true;try{await saveContact(form.value);visible.value=false;ElMessage.success('联系人已保存');await changed()}finally{saving.value=false}}
async function primary(row){await setPrimaryContact(row.id);ElMessage.success('主要联系人已更新');await changed()}
async function status(row){try{await ElMessageBox.confirm(row.status===1?'停用后不再用于新跟进，历史沟通记录保留。':'确认重新启用该联系人？','联系人状态',{type:'warning'})}catch{return}await setContactStatus(row.id,row.status===1?0:1);ElMessage.success('状态已更新');await changed()}
onMounted(load)
</script>
<style scoped>.contact-card{padding:20px}.contact-header{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.contact-header h3{font-size:16px;margin:0}</style>
