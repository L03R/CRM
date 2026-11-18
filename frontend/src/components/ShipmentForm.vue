<template>
  <el-dialog v-model="visible" title="登记实际发货" width="min(1000px,96vw)" append-to-body destroy-on-close>
    <el-alert title="仅登记已经实际发出的商品。数量以每个订单产品剩余待发量为上限，本页面不核算库存。" type="info" :closable="false" />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="shipment-form">
      <div class="grid">
        <el-form-item label="收货人" prop="recipientName"><el-input v-model.trim="form.recipientName" maxlength="64" /></el-form-item>
        <el-form-item label="收货电话" prop="recipientPhone"><el-input v-model.trim="form.recipientPhone" maxlength="32" /></el-form-item>
        <el-form-item label="实际发货时间" prop="shippedAt"><el-date-picker v-model="form.shippedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="物流公司" prop="carrier"><el-input v-model.trim="form.carrier" maxlength="64" placeholder="自送可填写企业自送" /></el-form-item>
      </div>
      <el-form-item label="收货地址" prop="shippingAddress"><el-input v-model.trim="form.shippingAddress" maxlength="255" /></el-form-item>
      <el-form-item label="物流单号" prop="trackingNo"><el-input v-model.trim="form.trackingNo" maxlength="128" placeholder="自送填写可追溯的交接单号" /></el-form-item>
      <el-table :data="lines"><el-table-column prop="productName" label="产品" min-width="160" /><el-table-column prop="skuCode" label="SKU" min-width="170" /><el-table-column prop="quantity" label="订购" width="80" /><el-table-column prop="shippedQuantity" label="已发" width="80" /><el-table-column label="本次发货" width="180"><template #default="{row}"><el-input-number v-model="row.current" :min="0" :max="row.quantity-row.shippedQuantity" :precision="0" controls-position="right" style="width:150px" /></template></el-table-column></el-table>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" maxlength="500" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">确认登记发货</el-button></template>
  </el-dialog>
</template>
<script setup>
import {ref} from 'vue'
import {ElMessage} from 'element-plus'
import {createShipment} from '@/api/delivery'
import {localTime} from '@/config/workflow'
const emit=defineEmits(['saved']),visible=ref(false),saving=ref(false),form=ref({}),lines=ref([]),formRef=ref()
const rules=Object.fromEntries(['recipientName','recipientPhone','shippingAddress','carrier','trackingNo','shippedAt'].map(key=>[key,[{required:true,message:'请填写此项'}]]))
function open(delivery) {
  form.value={orderId:delivery.orderId,orderVersion:delivery.orderVersion,requestKey:crypto.randomUUID(),recipientName:delivery.recipientName,recipientPhone:delivery.recipientPhone,shippingAddress:delivery.shippingAddress,shippedAt:localTime().slice(0,19),carrier:'',trackingNo:'',remark:''}
  lines.value=delivery.items.filter(line=>line.quantity>line.shippedQuantity).map(line=>({...line,current:0}))
  visible.value=true
}
async function save() {
  try {await formRef.value.validate()} catch {return}
  const items=lines.value.filter(line=>line.current>0).map(line=>({orderItemId:line.id,quantity:line.current}))
  if(!items.length) {ElMessage.warning('请填写至少一项实际发货数量');return}
  saving.value=true
  try {await createShipment({...form.value,items});visible.value=false;ElMessage.success('发货已登记，交付状态已自动更新');emit('saved')} finally {saving.value=false}
}
defineExpose({open})
</script>
<style scoped>.shipment-form{margin-top:20px}.grid{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}@media(max-width:700px){.grid{grid-template-columns:1fr}}</style>
