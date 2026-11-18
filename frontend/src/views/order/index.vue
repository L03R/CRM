<template>
  <div class="page"><div class="page-header"><div><h2 class="page-title">销售订单</h2><p class="page-desc">从客户已接受的最终报价生成草稿，核对收货和下单依据后确认。</p></div></div>
    <div class="card order-card"><div class="toolbar"><el-input v-model="query.keyword" placeholder="订单号 / 客户 / 报价单号 / 负责人" clearable style="width:300px" @keyup.enter="load(true)" /><el-select v-model="query.status" placeholder="订单状态" clearable style="width:180px" @change="load(true)"><el-option v-for="(label,key) in orderStates" :key="key" :label="label" :value="key" /></el-select><el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="生成开始日期" end-placeholder="结束日期" style="width:280px" @change="load(true)" /><el-button @click="load(true)">查询</el-button></div>
      <el-table v-loading="loading" :data="rows"><el-table-column prop="orderNo" label="订单号" min-width="210" /><el-table-column prop="customerName" label="客户" min-width="220" /><el-table-column label="来源报价" min-width="210"><template #default="{row}">{{ row.quoteNo }} · V{{ row.versionNo }}</template></el-table-column><el-table-column label="金额" width="145"><template #default="{row}">{{ money(row.totalAmount) }}</template></el-table-column><el-table-column label="状态" width="160"><template #default="{row}"><el-tag>{{ orderStates[row.status] }}</el-tag></template></el-table-column><el-table-column prop="ownerName" label="客户负责人" width="110" /><el-table-column prop="promisedDate" label="承诺交期" width="120" /><el-table-column label="生成时间" width="170"><template #default="{row}">{{ timeText(row.createdAt) }}</template></el-table-column><el-table-column label="操作" width="180" fixed="right"><template #default="{row}"><el-button link type="primary" @click="detail.open(row.id)">查看 / 处理</el-button><el-button v-if="row.actions.includes('delete')" link type="danger" @click="remove(row)">删除</el-button></template></el-table-column></el-table>
      <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="load(true)" />
    </div><OrderDetail ref="detail" @changed="load" />
  </div>
</template>
<script setup>
import {onMounted,reactive,ref} from 'vue'
import {useRoute} from 'vue-router'
import {pageOrders,deleteOrder} from '@/api/order'
import {ElMessage,ElMessageBox} from 'element-plus'
import {money} from '@/config/quotation'
import {timeText} from '@/config/workflow'
import {orderStates} from '@/config/order'
import OrderDetail from '@/components/OrderDetail.vue'
const route=useRoute(),query=reactive({keyword:'',status:null,page:1,size:20}),rows=ref([]),total=ref(0),loading=ref(false),detail=ref()
const dates=ref([])
async function load(reset=false){if(reset===true)query.page=1;loading.value=true;try{const result=await pageOrders({...query,dateFrom:dates.value?.[0],dateTo:dates.value?.[1]});rows.value=result.records;total.value=result.total}finally{loading.value=false}}
async function remove(row){
  try{await ElMessageBox.confirm('删除后订单从列表移除，原明细、取消原因和历史均保留。确认删除？','删除销售订单',{type:'warning',confirmButtonText:'删除'})}catch{return}
  await deleteOrder(row.id);ElMessage.success('订单已删除');await load()
}
onMounted(async()=>{await load();if(route.query.orderId)await detail.value.open(route.query.orderId)})
</script>
<style scoped>.order-card{padding:20px}</style>
