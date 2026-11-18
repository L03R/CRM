<template>
  <div class="page"><div class="page-header"><div><h2 class="page-title">发货记录</h2><p class="page-desc">发货从订单交付详情登记，支持分批发货。错误登记可留痕更正，实际退货需另行处理。</p></div></div>
    <div class="card shipment-card"><div class="toolbar"><el-input v-model="query.keyword" placeholder="发货单号 / 物流单号 / 订单 / 客户" clearable style="width:340px" @keyup.enter="load(true)" /><el-select v-model="query.status" clearable placeholder="记录状态" style="width:150px" @change="load(true)"><el-option label="有效记录" value="POSTED" /><el-option label="已更正作废" value="VOID" /></el-select><el-button @click="load(true)">查询</el-button></div>
    <el-table v-loading="loading" :data="rows"><el-table-column prop="shipmentNo" label="发货单号" min-width="210" /><el-table-column prop="orderNo" label="订单号" min-width="210" /><el-table-column prop="customerName" label="客户" min-width="200" /><el-table-column prop="carrier" label="物流公司" width="120" /><el-table-column prop="trackingNo" label="物流单号" min-width="180" /><el-table-column label="发货时间" width="170"><template #default="{row}">{{ timeText(row.shippedAt) }}</template></el-table-column><el-table-column prop="actorName" label="登记人" width="100" /><el-table-column label="状态" width="120"><template #default="{row}">{{ row.status==='POSTED'?'有效':'已更正作废' }}</template></el-table-column><el-table-column label="操作" fixed="right" width="80"><template #default="{row}"><el-button link type="primary" @click="detail.open(row.id)">详情</el-button></template></el-table-column></el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="load(true)" /></div><ShipmentDetail ref="detail" @changed="load" />
  </div>
</template>
<script setup>
import {onMounted,reactive,ref} from 'vue'
import {pageShipments} from '@/api/delivery'
import {timeText} from '@/config/workflow'
import ShipmentDetail from '@/components/ShipmentDetail.vue'
const query=reactive({keyword:'',status:null,page:1,size:20}),rows=ref([]),total=ref(0),loading=ref(false),detail=ref()
async function load(reset=false) {if(reset===true)query.page=1;loading.value=true;try{const data=await pageShipments(query);rows.value=data.records;total.value=data.total}finally{loading.value=false}}
onMounted(load)
</script>
<style scoped>.shipment-card{padding:20px}</style>
