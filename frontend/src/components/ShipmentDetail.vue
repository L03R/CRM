<template>
  <el-drawer v-model="visible" title="发货记录详情" size="min(950px,96vw)" append-to-body>
    <template v-if="shipment">
      <h2>{{ shipment.shipmentNo }} <el-tag>{{ shipment.status==='POSTED'?'有效记录':'已更正作废' }}</el-tag></h2>
      <el-descriptions :column="2" border><el-descriptions-item label="订单">{{ shipment.orderNo }}</el-descriptions-item><el-descriptions-item label="客户">{{ shipment.customerName }}</el-descriptions-item><el-descriptions-item label="收货人 / 电话">{{ shipment.recipientName }} / {{ shipment.recipientPhone }}</el-descriptions-item><el-descriptions-item label="地址">{{ shipment.shippingAddress }}</el-descriptions-item><el-descriptions-item label="物流公司">{{ shipment.carrier }}</el-descriptions-item><el-descriptions-item label="物流单号">{{ shipment.trackingNo }}</el-descriptions-item><el-descriptions-item label="发货时间">{{ timeText(shipment.shippedAt) }}</el-descriptions-item><el-descriptions-item label="登记人">{{ shipment.actorName }}</el-descriptions-item><el-descriptions-item label="备注">{{ shipment.remark || '—' }}</el-descriptions-item><el-descriptions-item v-if="shipment.voidReason" label="更正原因">{{ shipment.voidReason }}</el-descriptions-item></el-descriptions>
      <el-table :data="shipment.items"><el-table-column prop="productName" label="产品" min-width="160" /><el-table-column prop="skuCode" label="SKU" min-width="180" /><el-table-column prop="specification" label="规格" min-width="220" /><el-table-column prop="quantity" label="本次发货数量" width="120" /></el-table>
      <el-button v-if="store.hasPerm('aftersale:add') && shipment.status==='POSTED' && ['CONFIRMED','COMPLETED'].includes(deliverySource?.orderStatus)" type="primary" @click="registerAfterSale">登记此批次售后</el-button>
      <el-button v-if="canVoid" type="warning" :loading="saving" @click="correct">更正错误登记</el-button>
    </template>
  </el-drawer>
</template>
<script setup>
import {ref} from 'vue'
import {useRouter} from 'vue-router'
import {useUserStore} from '@/store/user'
import {findMenuPath} from '@/utils/menu'
import {ElMessage,ElMessageBox} from 'element-plus'
import {getShipment,getDelivery,voidShipment} from '@/api/delivery'
import {timeText} from '@/config/workflow'
const router=useRouter(),store=useUserStore(),deliverySource=ref(null)
const emit=defineEmits(['changed']),visible=ref(false),shipment=ref(null),canVoid=ref(false),saving=ref(false)
async function open(id) {shipment.value=await getShipment(id);const delivery=await getDelivery(shipment.value.orderId);deliverySource.value=delivery;canVoid.value=shipment.value.status==='POSTED' && delivery.actions.includes('voidShipment');visible.value=true}
function registerAfterSale(){const path=findMenuPath(store.menuTree,'delivery/after-sale');if(path){visible.value=false;router.push({path,query:{create:'1',customerId:deliverySource.value.customerId,customerName:shipment.value.customerName,orderId:shipment.value.orderId,shipmentId:shipment.value.id}})}}
async function correct() {
  let reason
  try {reason=(await ElMessageBox.prompt('仅用于录入错误，原记录保留并重新计算已发数量。客户实际退货不能使用此操作。','更正错误发货登记',{inputType:'textarea',inputValidator:value=>!!value?.trim() && value.length<=500 || '请填写1-500字原因'})).value} catch {return}
  saving.value=true
  try {await voidShipment(shipment.value.id,reason);ElMessage.success('错误登记已更正');await open(shipment.value.id);emit('changed')} finally {saving.value=false}
}
defineExpose({open})
</script>
