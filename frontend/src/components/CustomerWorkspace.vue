<template>
  <div class="page">
    <div class="page-header"><div><h2 class="page-title">{{ pool?'客户公海':'客户档案' }}</h2><p class="page-desc">{{ pool?'经理审核进入公海，按部门范围领取；保留完整客户和跟进历史。':'暂无采购需求可以继续维护；有具体采购需求再建立商机。' }}</p></div><el-button v-if="!pool && store.hasPerm('customer:add')" type="primary" @click="openForm()">新增客户</el-button></div>
    <div class="card">
      <div class="toolbar"><el-input v-model="query.keyword" placeholder="客户名称 / 联系人" clearable style="width:260px" @keyup.enter="load(true)" /><el-button type="primary" @click="load(true)">查询</el-button></div>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column prop="companyName" label="客户名称" min-width="230" />
        <el-table-column label="类型" width="120"><template #default="{row}">{{ customerTypes[row.customerType] }}</template></el-table-column>
        <el-table-column prop="region" label="地区" width="120" />
        <el-table-column prop="contactName" label="主要联系人" width="110" />
        <el-table-column label="负责人" width="110"><template #default="{row}">{{ row.ownerName || '公海待领取' }}</template></el-table-column>
        <el-table-column label="合作状态" width="110"><template #default="{row}">{{ relationships[row.relationship] }}</template></el-table-column>
        <el-table-column label="标记" width="150"><template #default="{row}"><el-tag v-if="row.protectedCustomer" type="warning">重要客户</el-tag><el-tag v-if="row.pendingRequestId" type="info">公海申请待审核</el-tag></template></el-table-column>
        <el-table-column v-if="pool" prop="poolDeptName" label="公海所属部门" width="150" />
        <el-table-column v-else label="下次跟进" width="180"><template #default="{row}">{{ timeText(row.nextFollowAt) }}</template></el-table-column>
        <el-table-column prop="activeOpportunities" label="未结束商机" width="105" />
        <el-table-column label="操作" width="240" fixed="right"><template #default="{row}">
          <div class="workflow-actions">
          <el-button link type="primary" @click="detailPanel.open('CUSTOMER',row.id)">详情</el-button>
          <el-button v-if="hasAction(row,'follow')" class="action-follow" link type="primary" @click="followDialog.open('CUSTOMER',row)">记录跟进</el-button>
          <el-button v-if="hasAction(row,'claim')" class="action-follow" link type="success" @click="claim(row)">领取客户</el-button>
          <el-button v-if="pool && hasAction(row,'assign')" class="action-more" link type="primary" @click="assignDialog.open('CUSTOMER',row)">分配客户</el-button>
          <el-dropdown v-if="!pool && row.actions.some(a=>['edit','assign','opportunity','poolApprove'].includes(a) || a==='poolRequest' && !row.pendingRequestId)" class="action-more" @command="command=>act(command,row)" trigger="click">
            <el-button link type="primary">更多</el-button>
            <template #dropdown><el-dropdown-menu>
              <el-dropdown-item v-if="hasAction(row,'edit')" command="edit">编辑资料</el-dropdown-item>
              <el-dropdown-item v-if="hasAction(row,'assign')" command="assign">转交负责人</el-dropdown-item>
              <el-dropdown-item v-if="hasAction(row,'opportunity')" command="opportunity">建立商机</el-dropdown-item>
              <el-dropdown-item v-if="hasAction(row,'poolRequest') && !row.pendingRequestId" command="request">申请退回公海</el-dropdown-item>
              <el-dropdown-item v-if="hasAction(row,'poolApprove')" command="review">审核公海申请</el-dropdown-item>
            </el-dropdown-menu></template>
          </el-dropdown>
          </div>
        </template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="load(true)" />
    </div>
    <el-dialog v-model="formVisible" :title="form.id?'编辑客户':'新增客户'" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="customerRules" label-width="105px"><ProspectFields :form="form" :can-protect="canProtect" /></el-form>
      <template #footer><el-button @click="formVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="reviewVisible" title="审核退回公海申请" width="520px" destroy-on-close>
      <p>客户：{{ reviewTarget.companyName }}</p><p>申请原因：{{ reviewTarget.poolRequestReason }}</p>
      <el-alert title="有未结束商机或重要客户保护时不能通过。通过后解除负责人，原记录和沟通历史保留。" type="warning" :closable="false" />
      <el-form ref="reviewRef" :model="reviewForm" label-width="90px">
        <el-form-item label="审核结果" prop="approve" :rules="[{required:true,type:'boolean',message:'请选择审核结果'}]"><el-radio-group v-model="reviewForm.approve"><el-radio :value="true">通过</el-radio><el-radio :value="false">驳回</el-radio></el-radio-group></el-form-item>
        <el-form-item label="审核说明" prop="note" :rules="[{required:true,message:'请填写审核说明'}]"><el-input v-model="reviewForm.note" type="textarea" :rows="3" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="reviewVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitReview">确认审核</el-button></template>
    </el-dialog>
    <WorkflowDetail ref="detailPanel" @changed="load" /><WorkflowFollowDialog ref="followDialog" @saved="load" /><WorkflowAssignDialog ref="assignDialog" @saved="load" /><OpportunityDialog ref="opportunityDialog" @saved="load" />
  </div>
</template>
<script setup>
import {computed,onMounted,reactive,ref} from 'vue'
import {useRoute} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import {useUserStore} from '@/store/user'
import {pageCustomers,saveCustomer,requestCustomerPool,reviewCustomerPool,claimCustomer} from '@/api/workflow'
import {customerTypes,relationships,prospectRules,emptyProspect,hasAction,timeText} from '@/config/workflow'
import ProspectFields from './ProspectFields.vue'
import WorkflowDetail from './WorkflowDetail.vue'
import WorkflowFollowDialog from './WorkflowFollowDialog.vue'
import WorkflowAssignDialog from './WorkflowAssignDialog.vue'
import OpportunityDialog from './OpportunityDialog.vue'
const props=defineProps({pool:Boolean}), store=useUserStore()
const query=reactive({keyword:String(useRoute().query.keyword || ''),page:1,size:20}), rows=ref([]), total=ref(0), loading=ref(false), saving=ref(false)
const formVisible=ref(false), form=ref({}), formRef=ref(), canProtect=ref(false)
const reviewVisible=ref(false), reviewTarget=ref({}), reviewForm=ref({}), reviewRef=ref()
const detailPanel=ref(), followDialog=ref(), assignDialog=ref(), opportunityDialog=ref()
const {demand,targetDeptId,contactName,...baseCustomerRules}=prospectRules
const customerRules=computed(()=>form.value.id?baseCustomerRules:{...baseCustomerRules,contactName})
async function load(resetPage=false){if(resetPage===true)query.page=1;loading.value=true;try{const result=await pageCustomers(query,props.pool);rows.value=result.records;total.value=result.total}finally{loading.value=false}}
function openForm(row=null){form.value=row?{...row}:emptyProspect();canProtect.value=row?hasAction(row,'protect'):store.hasPerm('customer:assign');formVisible.value=true}
async function submit(){try{await formRef.value.validate()}catch{return}if(!form.value.id && !form.value.phone && !form.value.email){ElMessage.warning('电话和邮箱至少填写一项');return}saving.value=true;try{await saveCustomer(form.value);formVisible.value=false;ElMessage.success('客户已保存');await load()}finally{saving.value=false}}
async function act(command,row){
  if(command==='edit'){openForm(row);return}
  if(command==='assign'){await assignDialog.value.open('CUSTOMER',row);return}
  if(command==='opportunity'){await opportunityDialog.value.open(null,row);return}
  if(command==='review'){reviewTarget.value=row;reviewForm.value={approve:null,note:''};reviewVisible.value=true;return}
  let reason
  try{reason=(await ElMessageBox.prompt('请填写退回原因，经理审核通过前仍由当前销售负责。','申请退回公海',{inputType:'textarea',inputValidator:value=>!!value?.trim() && value.trim().length<=500 || '请填写1-500字原因'})).value}catch{return}
  await requestCustomerPool(row.id,reason);ElMessage.success('申请已提交，等待经理审核');await load()
}
async function submitReview(){try{await reviewRef.value.validate()}catch{return}saving.value=true;try{await reviewCustomerPool(reviewTarget.value.id,reviewTarget.value.pendingRequestId,reviewForm.value);reviewVisible.value=false;ElMessage.success('审核已完成');await load()}finally{saving.value=false}}
async function claim(row){try{await ElMessageBox.confirm('领取“'+row.companyName+'”并由你负责后续跟进？','领取客户',{type:'info'})}catch{return}await claimCustomer(row.id);ElMessage.success('领取成功，可在客户档案中跟进');await load()}
onMounted(load)
</script>
