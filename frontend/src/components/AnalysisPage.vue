<template>
  <div class="page" v-loading="loading">
    <div class="page-header"><div><h2 class="page-title">{{ title }}</h2><p class="page-desc">按当前账号可访问的数据统计，日期筛选以档案或商机创建日期为准。</p></div></div>
    <div class="card filters"><el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" /><el-button type="primary" @click="load">查询</el-button></div>
    <div class="metrics">
      <div class="card metric"><span>{{ kind==='lead'?'线索总数':kind==='customer'?'客户总数':'商机总数' }}</span><strong>{{ total }}</strong></div>
      <template v-if="kind==='lead'"><div class="card metric"><span>已转客户</span><strong>{{ count('CONVERTED') }}</strong></div><div class="card metric"><span>转化率</span><strong>{{ ratio(count('CONVERTED'),total) }}</strong></div></template>
      <template v-if="kind==='customer'"><div class="card metric"><span>今日待跟进客户 · 当前</span><strong>{{ result.metrics.todayFollow || 0 }}</strong></div><div class="card metric"><span>已逾期客户 · 当前</span><strong>{{ result.metrics.overdueFollow || 0 }}</strong></div></template>
      <template v-if="kind==='business'"><div class="card metric"><span>进行中预计金额</span><strong class="money">¥ {{ money(activeAmount) }}</strong></div><div class="card metric"><span>结束商机赢单率</span><strong>{{ ratio(count('WON'),count('WON')+count('LOST')) }}</strong></div></template>
    </div>
    <el-alert :title="definition" type="info" :closable="false" show-icon />
    <div class="charts"><div class="card"><h3>{{ kind==='customer'?'客户类型分布':kind==='lead'?'线索状态分布':'商机阶段分布' }}</h3><BusinessChart :points="result.distribution" :labels="labels" /></div><div class="card"><h3>每日新增数量</h3><BusinessChart :points="result.trend" /></div></div>
    <div v-if="kind==='lead'" class="card"><h3>线索来源分布</h3><BusinessChart :points="result.sources" /></div>
    <div v-if="kind==='business'" class="card"><h3>阶段数量与预计金额</h3><el-table :data="result.distribution"><el-table-column label="阶段"><template #default="{row}">{{ labels[row.name] }}</template></el-table-column><el-table-column prop="value" label="数量" /><el-table-column label="预计金额（元）"><template #default="{row}">{{ money(row.amount) }}</template></el-table-column></el-table><p class="text-sub">金额来自商机预计金额，不代表实际订单金额、收入或回款；未填写金额的商机不贡献金额。</p></div>
  </div>
</template>
<script setup>
import {computed,onMounted,ref} from 'vue'
import {ElMessage} from 'element-plus'
import {getReport} from '@/api/dashboard'
import {leadStatuses,customerTypes,opportunityStages} from '@/config/workflow'
import BusinessChart from './BusinessChart.vue'
const props=defineProps({kind:String,title:String})
const today=new Date(), localDate=d=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
const range=ref([localDate(new Date(today.getFullYear(),today.getMonth(),1)),localDate(today)])
const loading=ref(false),result=ref({distribution:[],trend:[],sources:[],metrics:{}})
const labels=computed(()=>({lead:leadStatuses,customer:customerTypes,business:opportunityStages})[props.kind])
const total=computed(()=>result.value.distribution.reduce((sum,p)=>sum+p.value,0))
const activeAmount=computed(()=>result.value.distribution.filter(p=>!['WON','LOST'].includes(p.name)).reduce((sum,p)=>sum+Number(p.amount),0))
const definition=computed(()=>({lead:'转化率 = 所选期间创建的线索中，目前已转客户的数量 ÷ 该期间线索总数。状态按当前状态统计。',customer:'客户统计只包含当前有负责人的客户，公海不计入。今日与逾期指标按当前全部可见客户的维护计划统计，不受创建日期筛选限制。',business:'阶段按当前状态统计；赢单率 = 已赢单 ÷（已赢单 + 已丢单）。当前负责人跟随客户归属，预计金额不代表收入。'})[props.kind])
const count=name=>result.value.distribution.find(p=>p.name===name)?.value || 0
const ratio=(n,d)=>d?`${(n/d*100).toFixed(1)}%`:'—'
const money=n=>Number(n || 0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
async function load(){if(!range.value?.length){ElMessage.warning('请选择日期范围');return}loading.value=true;try{result.value=await getReport(props.kind,{start:range.value[0],end:range.value[1]})}finally{loading.value=false}}
onMounted(load)
</script>
<style scoped>
.card{padding:20px}.metrics>.card,.charts>.card{margin:0}
.filters{display:flex;gap:12px;align-items:center}.metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px;margin:20px 0}.metric{display:flex;flex-direction:column;gap:14px}.metric span{color:#64748b;font-size:13px}.metric strong{font-size:30px;color:#263550}.metric .money{font-size:26px}.charts{display:grid;grid-template-columns:1fr 1fr;gap:20px;margin:20px 0}.charts .card{min-width:0}h3{font-size:15px;margin:0 0 20px}@media(max-width:900px){.charts{grid-template-columns:1fr}.metrics{grid-template-columns:1fr}.filters{flex-wrap:wrap}}
</style>
