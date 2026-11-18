<template>
  <div class="page" v-loading="loading">
    <div class="page-header"><div><h2 class="page-title">工作概览</h2><p class="page-desc">{{ store.displayName }}，{{ today }} · 业务数据按你的权限范围展示</p></div><el-button @click="load">刷新</el-button></div>
    <div v-if="cards.length" class="metric-grid">
      <button v-for="card in cards" :key="card.key" class="card metric" @click="navigate(card.component,card.query)"><span>{{ card.label }}</span><strong>{{ data.metrics[card.key] }}</strong><small>查看详情 →</small></button>
    </div>
    <div v-if="canTodo && cards.length" class="card overview-todos"><div class="section-head"><h3>需要处理的事项</h3><el-button link type="primary" @click="navigate('workbench/todo')">查看全部待办</el-button></div><p class="text-sub">展示最早的5项业务待办，处理后自动更新。</p>
      <el-table :data="data.todos"><el-table-column prop="title" label="线索 / 客户" min-width="220" /><el-table-column prop="reason" label="事项" width="160" /><el-table-column label="计划 / 提交时间" width="190"><template #default="{row}">{{ timeText(row.dueAt) }}</template></el-table-column><el-table-column label="操作" width="90"><template #default="{row}"><el-button link type="primary" @click="process(row)">去处理</el-button></template></el-table-column></el-table>
    </div>
    <el-empty v-if="!cards.length" description="当前岗位暂无业务概览，可在“我的日程”安排个人工作" :image-size="90" />
  </div>
</template>
<script setup>
import {computed,onMounted,ref} from 'vue'
import {useRouter} from 'vue-router'
import {useUserStore} from '@/store/user'
import {getOverview} from '@/api/dashboard'
import {timeText} from '@/config/workflow'
import {findMenuPath} from '@/utils/menu'
import {workItemLocation} from '@/utils/workItem'
const store=useUserStore(),router=useRouter(),loading=ref(false),data=ref({metrics:{},todos:[]})
const today=new Date().toLocaleDateString('zh-CN',{month:'long',day:'numeric',weekday:'long'})
const canTodo=computed(()=>!!findMenuPath(store.menuTree,'workbench/todo'))
const followCards=[{key:'todayFollow',label:'今日待跟进客户',component:'workbench/todo',query:{type:'CUSTOMER_FOLLOW',due:'TODAY'}},{key:'overdueFollow',label:'已逾期跟进客户',component:'workbench/todo',query:{type:'CUSTOMER_FOLLOW',due:'OVERDUE'}}]
const managerCards=[{key:'assignPending',label:'待分配线索',component:'workbench/todo',query:{type:'ASSIGN'}},{key:'reviewPending',label:'待审核公海申请',component:'workbench/todo',query:{type:'REVIEW'}}]
const marketCards=[{key:'leadTotal',label:'可见线索总数',component:'clue/index'},{key:'leadContacting',label:'跟进中线索',component:'clue/index',query:{status:'CONTACTING'}},{key:'leadConverted',label:'已转客户线索',component:'clue/index',query:{status:'CONVERTED'}}]
const cards=computed(()=>{
  const metrics=data.value.metrics,manager=Object.hasOwn(metrics,'assignPending') || Object.hasOwn(metrics,'reviewPending')
  const choices=manager?[...managerCards,followCards[1],{key:'activeBusiness',label:'进行中商机',component:'business/index'}]:Object.hasOwn(metrics,'customerTotal')?[...followCards,{key:'activeBusiness',label:'进行中商机',component:'business/index'}]:[...marketCards]
  choices.push({key:'aftersalePending',label:'有跟进计划的售后',component:'workbench/todo',query:{type:'AFTERSALE_FOLLOW'}})
  return choices.filter(card=>Object.hasOwn(metrics,card.key) && findMenuPath(store.menuTree,card.component)).slice(0,4)
})
function process(row){const target=workItemLocation(store.menuTree,row);if(target)router.push(target)}
function navigate(component,query={}){const path=findMenuPath(store.menuTree,component);if(path)router.push({path,query})}
async function load(){loading.value=true;try{data.value=await getOverview()}finally{loading.value=false}}
onMounted(load)
</script>
<style scoped>
.metric-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:18px;margin-bottom:22px}.metric-grid>.card{margin:0}.metric{padding:22px;display:flex;flex-direction:column;align-items:flex-start;gap:16px;text-align:left;cursor:pointer;font-family:inherit}.metric span{color:#64748b;font-size:14px}.metric strong{color:#263550;font-size:32px}.metric small{color:#7383ab}.metric:hover{border-color:#7e8ef6}.overview-todos{padding:20px}.section-head{display:flex;align-items:center;justify-content:space-between}.section-head h3{font-size:16px;margin:0}@media(max-width:600px){.metric-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
