<template>
  <div class="page">
    <div class="page-header"><div><h2 class="page-title">待办事项</h2><p class="page-desc">根据当前业务状态自动更新，请进入对应业务处理。经理可查看管理范围内的事项。</p></div></div>
    <div class="card task-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="线索 / 客户 / 售后问题" clearable style="width:230px" @keyup.enter="load(true)" />
        <el-select v-model="query.type" style="width:160px" @change="load(true)"><el-option v-for="(name,key) in types" :key="key" :label="name" :value="key" /></el-select>
        <el-select v-model="query.due" style="width:160px" @change="load(true)"><el-option label="全部到期情况" value="ALL" /><el-option label="今日跟进" value="TODAY" /><el-option label="逾期跟进" value="OVERDUE" /></el-select>
        <el-button type="primary" @click="load(true)">查询</el-button><el-button @click="reset">重置</el-button>
      </div>
      <el-table v-loading="loading" :data="rows">
        <el-table-column prop="title" label="线索 / 客户 / 售后问题" min-width="240" />
        <el-table-column prop="reason" label="事项类型" width="160" />
        <el-table-column label="计划 / 提交时间" width="190"><template #default="{row}">{{ timeText(row.dueAt) }}</template></el-table-column>
        <el-table-column label="跟进到期情况" width="140"><template #default="{row}"><el-tag v-if="row.reason.endsWith('跟进')" :type="isOverdue(row)?'danger':'info'">{{ isOverdue(row)?'已逾期':'待跟进' }}</el-tag><span v-else>待处理</span></template></el-table-column>
        <el-table-column label="操作" width="160"><template #default="{row}"><el-button link type="primary" @click="showDetail(row)">详情</el-button><el-button link type="primary" @click="process(row)">去处理</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="load(true)" />
    </div>
    <WorkflowDetail ref="detail" />
  </div>
</template>
<script setup>
import {onMounted,reactive,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {useUserStore} from '@/store/user'
import {getTasks} from '@/api/dashboard'
import {timeText} from '@/config/workflow'
import {workItemLocation} from '@/utils/workItem'
import WorkflowDetail from '@/components/WorkflowDetail.vue'
const route=useRoute(),router=useRouter(),store=useUserStore(),detail=ref()
const types={ALL:'全部类型',LEAD_FOLLOW:'线索跟进',CUSTOMER_FOLLOW:'客户跟进',AFTERSALE_FOLLOW:'售后跟进',ASSIGN:'待分配线索',REVIEW:'待审核公海申请'}
const query=reactive({keyword:'',type:route.query.type || 'ALL',due:route.query.due || 'ALL',page:1,size:20})
const rows=ref([]),total=ref(0),loading=ref(false)
const isOverdue=row=>new Date(row.dueAt).getTime()<Date.now()
async function load(reset=false){if(reset===true)query.page=1;loading.value=true;try{const result=await getTasks(query);rows.value=result.records;total.value=result.total}finally{loading.value=false}}
function reset(){Object.assign(query,{keyword:'',type:'ALL',due:'ALL',page:1});load()}
function process(row){const target=workItemLocation(store.menuTree,row);if(target)router.push(target)}
function showDetail(row){if(row.kind==='AFTERSALE')process(row);else detail.value.open(row.kind,row.id)}
onMounted(load)
</script>
<style scoped>.task-card{padding:20px}</style>
