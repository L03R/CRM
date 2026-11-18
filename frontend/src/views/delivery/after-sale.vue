<template>
  <div class="page"><div class="page-header"><div><h2 class="page-title">售后记录</h2><p class="page-desc">记录客户问题、追溯订单与发货来源，转交后持续跟踪反馈，具体处理在相关部门执行。</p></div><el-button v-if="store.hasPerm('aftersale:add')" type="primary" @click="form.open()">登记售后问题</el-button></div>
    <div class="card aftersale-card"><div class="toolbar"><el-input v-model="query.keyword" placeholder="售后单号 / 问题 / 客户 / 订单" clearable style="width:290px" @keyup.enter="load(true)" /><el-select v-model="query.status" clearable placeholder="全部状态" style="width:140px" @change="load(true)"><el-option v-for="(label,key) in afterSaleStates" :key="key" :label="label" :value="key" /></el-select><el-select v-model="query.issueType" clearable placeholder="问题类型" style="width:140px" @change="load(true)"><el-option v-for="(label,key) in afterSaleTypes" :key="key" :label="label" :value="key" /></el-select><el-select v-model="query.ownerId" clearable placeholder="跟进负责人" style="width:160px" @change="load(true)"><el-option v-for="person in owners" :key="person.id" :label="person.name" :value="person.id" /></el-select><el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="反馈开始日期" end-placeholder="结束日期" style="width:270px" @change="load(true)" /><el-checkbox v-model="query.sourcePending" @change="load(true)">来源待核实</el-checkbox><el-checkbox v-model="query.overdue" @change="load(true)">逾期未跟进</el-checkbox><el-button @click="load(true)">查询</el-button><el-button @click="reset">重置</el-button></div>
    <el-table v-loading="loading" :data="rows"><el-table-column prop="caseNo" label="售后单号" min-width="200" /><el-table-column prop="title" label="客户问题" min-width="220" /><el-table-column prop="customerName" label="客户" min-width="200" /><el-table-column label="来源订单" min-width="210"><template #default="{row}">{{ row.orderNo || '尚未明确' }} <el-tag v-if="row.sourcePending" type="warning">待核实</el-tag></template></el-table-column><el-table-column label="类型" width="110"><template #default="{row}">{{ afterSaleTypes[row.issueType] }}</template></el-table-column><el-table-column label="状态" width="110"><template #default="{row}"><el-tag>{{ afterSaleStates[row.status] }}</el-tag></template></el-table-column><el-table-column prop="ownerName" label="跟进负责人" width="110" /><el-table-column label="下次跟进" width="170"><template #default="{row}"><span :class="{overdue:row.status!=='CLOSED' && row.nextFollowAt && new Date(row.nextFollowAt)<new Date()}">{{ timeText(row.nextFollowAt) }}</span></template></el-table-column><el-table-column label="反馈时间" width="170"><template #default="{row}">{{ timeText(row.reportedAt) }}</template></el-table-column><el-table-column label="操作" width="100" fixed="right"><template #default="{row}"><el-button link type="primary" @click="detail.open(row.id)">查看 / 跟进</el-button></template></el-table-column></el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="load(true)" /></div>
    <AfterSaleForm ref="form" @saved="created" /><AfterSaleDetail ref="detail" @changed="load" />
  </div>
</template>
<script setup>
import {onMounted,reactive,ref,watch} from 'vue'
import {useRoute} from 'vue-router'
import {useUserStore} from '@/store/user'
import {pageAfterSales,afterSaleOwners} from '@/api/aftersale'
import {afterSaleStates,afterSaleTypes} from '@/config/aftersale'
import {timeText} from '@/config/workflow'
import AfterSaleForm from '@/components/AfterSaleForm.vue'
import AfterSaleDetail from '@/components/AfterSaleDetail.vue'
const store=useUserStore(),route=useRoute(),query=reactive({keyword:'',status:null,issueType:null,ownerId:null,sourcePending:false,overdue:false,page:1,size:20}),dates=ref([]),rows=ref([]),total=ref(0),loading=ref(false),owners=ref([]),form=ref(),detail=ref()
async function load(reset=false){if(reset===true)query.page=1;loading.value=true;try{const [data,people]=await Promise.all([pageAfterSales({...query,dateFrom:dates.value?.[0],dateTo:dates.value?.[1]}),afterSaleOwners()]);rows.value=data.records;total.value=data.total;owners.value=people}finally{loading.value=false}}
function reset(){Object.assign(query,{keyword:'',status:null,issueType:null,ownerId:null,sourcePending:false,overdue:false,page:1});dates.value=[];load()}
async function created(id){await load();await detail.value.open(id)}
async function fromRoute(){if(route.query.caseId)await detail.value.open(route.query.caseId);else if(route.query.create==='1' && store.hasPerm('aftersale:add'))await form.value.open({customerId:Number(route.query.customerId),customerName:route.query.customerName,orderId:route.query.orderId?Number(route.query.orderId):null,shipmentId:route.query.shipmentId?Number(route.query.shipmentId):null})}
onMounted(async()=>{await load();await fromRoute()})
watch(()=>route.fullPath,fromRoute)
</script>
<style scoped>.aftersale-card{padding:20px}.overdue{color:#f56c6c}</style>
