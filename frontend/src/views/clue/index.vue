<template>
  <div class="page">
    <div class="page-header"><div><h2 class="page-title">线索管理</h2><p class="page-desc">市场录入 → 经理分配 → 销售沟通 → 确认价值后转客户</p></div><el-button v-if="store.hasPerm('clue:add')" type="primary" @click="openForm()">新增线索</el-button></div>
    <el-alert title="市场录入的线索进入待分配池；销售录入默认由本人负责。未核实继续跟进，无效信息关闭，不能直接进入客户公海。" type="info" :closable="false" />
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="公司名称 / 联系人" clearable style="width:240px" @keyup.enter="load(true)" />
        <el-select v-model="query.status" clearable placeholder="线索状态" style="width:160px"><el-option v-for="(name,key) in leadStatuses" :key="key" :value="key" :label="name" /></el-select>
        <el-button type="primary" @click="load(true)">查询</el-button><el-button @click="reset">重置</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column prop="companyName" label="公司/线索" min-width="220" />
        <el-table-column prop="region" label="地区" width="120" />
        <el-table-column prop="contactName" label="主要联系人" width="110" />
        <el-table-column prop="source" label="来源" width="110" />
        <el-table-column label="状态" width="110"><template #default="{row}"><el-tag>{{ leadStatuses[row.status] }}</el-tag></template></el-table-column>
        <el-table-column label="负责人" width="110"><template #default="{row}">{{ row.ownerName || '待分配' }}</template></el-table-column>
        <el-table-column prop="targetDeptName" label="意向销售部门" width="140" />
        <el-table-column prop="creatorName" label="录入人" width="110" />
        <el-table-column label="下次跟进" width="180"><template #default="{row}">{{ timeText(row.nextFollowAt) }}</template></el-table-column>
        <el-table-column label="操作" width="220" fixed="right"><template #default="{row}">
          <div class="workflow-actions">
          <el-button link type="primary" @click="detailPanel.open('LEAD',row.id)">详情</el-button>
          <el-button v-if="hasAction(row,'follow')" class="action-follow" link type="primary" @click="followDialog.open('LEAD',row)">记录跟进</el-button>
          <el-dropdown v-if="row.actions.some(a=>a!=='follow')" class="action-more" @command="command=>act(command,row)" trigger="click">
            <el-button link type="primary">更多</el-button>
            <template #dropdown><el-dropdown-menu>
              <el-dropdown-item v-if="hasAction(row,'edit')" command="edit">编辑资料</el-dropdown-item>
              <el-dropdown-item v-if="hasAction(row,'assign')" command="assign">分配/转交</el-dropdown-item>
              <el-dropdown-item v-if="hasAction(row,'convert')" command="convert">转为客户</el-dropdown-item>
              <el-dropdown-item v-if="hasAction(row,'return')" command="return">退回待分配池</el-dropdown-item>
              <el-dropdown-item v-if="hasAction(row,'close')" command="invalid">标记无效</el-dropdown-item>
              <el-dropdown-item v-if="hasAction(row,'close')" command="close">关闭线索</el-dropdown-item>
            </el-dropdown-menu></template>
          </el-dropdown>
          </div>
        </template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="load(true)" />
    </div>
    <el-dialog v-model="formVisible" :title="form.id?'编辑线索':'新增线索'" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="prospectRules" label-width="110px"><ProspectFields :form="form" lead :departments="departments" /></el-form>
      <template #footer><el-button @click="formVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>
    <WorkflowDetail ref="detailPanel" />
    <WorkflowFollowDialog ref="followDialog" @saved="load" />
    <WorkflowAssignDialog ref="assignDialog" @saved="load" />
  </div>
</template>
<script setup>
import {onMounted,reactive,ref} from 'vue'
import {useRoute} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import {useUserStore} from '@/store/user'
import {pageLeads,saveLead,workflowDepartments,returnLead,closeLead,convertLead} from '@/api/workflow'
import {leadStatuses,prospectRules,emptyProspect,hasAction,timeText} from '@/config/workflow'
import ProspectFields from '@/components/ProspectFields.vue'
import WorkflowDetail from '@/components/WorkflowDetail.vue'
import WorkflowFollowDialog from '@/components/WorkflowFollowDialog.vue'
import WorkflowAssignDialog from '@/components/WorkflowAssignDialog.vue'
const store=useUserStore()
const query=reactive({keyword:String(useRoute().query.keyword || ''),status:useRoute().query.status || null,page:1,size:20}), rows=ref([]), total=ref(0), loading=ref(false)
const formVisible=ref(false), form=ref({}), formRef=ref(), departments=ref([]), saving=ref(false)
const detailPanel=ref(), followDialog=ref(), assignDialog=ref()
async function load(resetPage=false){if(resetPage===true)query.page=1;loading.value=true;try{const result=await pageLeads(query);rows.value=result.records;total.value=result.total}finally{loading.value=false}}
function reset(){Object.assign(query,{keyword:'',status:null,page:1});load()}
async function openForm(row=null){
  departments.value=await workflowDepartments()
  form.value=row?{...row}:emptyProspect()
  if(!row && store.hasPerm('clue:convert'))form.value.targetDeptId=store.userInfo.deptId
  formVisible.value=true
}
async function submit(){try{await formRef.value.validate()}catch{return}if(!form.value.phone && !form.value.email){ElMessage.warning('电话和邮箱至少填写一项');return}saving.value=true;try{await saveLead(form.value);formVisible.value=false;ElMessage.success('线索已保存');await load()}finally{saving.value=false}}
async function act(command,row){
  if(command==='edit'){await openForm(row);return}
  if(command==='assign'){await assignDialog.value.open('LEAD',row);return}
  if(command==='convert'){
    try{await ElMessageBox.confirm('确认已沟通并核实有合作价值？将创建客户档案，保留负责人和原沟通历史。','线索转客户',{type:'info'})}catch{return}
    const id=await convertLead(row.id);ElMessage.success('已转为客户 #'+id);await load();return
  }
  let reason
  try{reason=(await ElMessageBox.prompt('请填写实际原因，保留处理记录。',command==='return'?'退回待分配池':command==='invalid'?'标记无效':'关闭线索',{inputType:'textarea',inputValidator:value=>!!value?.trim() && value.trim().length<=500 || '请填写1-500字原因'})).value}catch{return}
  if(command==='return')await returnLead(row.id,reason)
  else await closeLead(row.id,command==='invalid'?'INVALID':'CLOSED',reason)
  ElMessage.success('线索状态已更新');await load()
}
onMounted(load)
</script>
