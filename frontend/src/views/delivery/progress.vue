<template>
  <div class="page"><div class="page-header"><div><h2 class="page-title">订单交付进度</h2><p class="page-desc">销售经理统筹交付，生产和仓储负责人更新执行情况，发货进度按产品自动汇总。</p></div></div>
    <div class="card delivery-card"><div class="toolbar"><el-input v-model="query.keyword" placeholder="订单号 / 客户 / 交付经理" clearable style="width:280px" @keyup.enter="load(true)" /><el-select v-model="query.status" clearable placeholder="交付状态" style="width:150px" @change="load(true)"><el-option v-for="(name,key) in deliveryStates" :key="key" :label="name" :value="key" /></el-select><el-checkbox v-model="query.overdue" @change="load(true)">仅延期订单</el-checkbox><el-button @click="load(true)">查询</el-button></div>
      <el-table v-loading="loading" :data="rows"><el-table-column prop="orderNo" label="订单号" min-width="210" /><el-table-column prop="customerName" label="客户" min-width="200" /><el-table-column label="交付状态" width="150"><template #default="{row}"><el-tag>{{ deliveryStates[row.status] }}</el-tag><el-tag v-if="row.overdue" type="danger">延期</el-tag></template></el-table-column><el-table-column prop="ownerName" label="销售" width="100" /><el-table-column prop="managerName" label="交付经理" width="110" /><el-table-column prop="productionName" label="生产负责人" width="110" /><el-table-column prop="warehouseName" label="仓储负责人" width="110" /><el-table-column prop="promisedDate" label="承诺交期" width="120" /><el-table-column prop="expectedDate" label="预计发货" width="120" /><el-table-column label="操作" width="110" fixed="right"><template #default="{row}"><el-button link type="primary" @click="detail.open(row.orderId)">查看 / 处理</el-button></template></el-table-column></el-table>
      <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="load(true)" />
    </div><DeliveryDetail ref="detail" @changed="load" />
  </div>
</template>
<script setup>
import {onMounted,reactive,ref} from 'vue'
import {useRoute} from 'vue-router'
import {pageDeliveries} from '@/api/delivery'
import {deliveryStates} from '@/config/delivery'
import DeliveryDetail from '@/components/DeliveryDetail.vue'
const query=reactive({keyword:'',status:null,overdue:false,page:1,size:20}),rows=ref([]),total=ref(0),loading=ref(false),detail=ref(),route=useRoute()
async function load(reset=false) {if(reset===true)query.page=1;loading.value=true;try{const data=await pageDeliveries(query);rows.value=data.records;total.value=data.total}finally{loading.value=false}}
onMounted(async()=>{await load();if(route.query.orderId)await detail.value.open(route.query.orderId)})
</script>
<style scoped>.delivery-card{padding:20px}</style>
