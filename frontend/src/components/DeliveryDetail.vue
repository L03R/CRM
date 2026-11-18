<template>
  <el-drawer v-model="visible" title="订单交付详情" size="min(1100px,96vw)" append-to-body>
    <template v-if="delivery">
      <h2>{{ delivery.orderNo }} <el-tag>{{ deliveryStates[delivery.status] }}</el-tag> <el-tag v-if="delivery.overdue" type="danger">已延期</el-tag></h2>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="客户 / 销售">{{ delivery.customerName }} / {{ delivery.ownerName }}</el-descriptions-item>
        <el-descriptions-item label="交付对接经理">{{ delivery.managerName || '待分配' }}</el-descriptions-item>
        <el-descriptions-item label="生产负责人">{{ delivery.productionName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备货发货负责人">{{ delivery.warehouseName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="客户要求 / 承诺交期">{{ delivery.requestedDate || '—' }} / {{ delivery.promisedDate }}</el-descriptions-item>
        <el-descriptions-item label="预计发货日期">{{ delivery.expectedDate || '待确认' }}</el-descriptions-item>
        <el-descriptions-item label="当前生产备货环节">{{ deliveryStates[delivery.preparation] }}</el-descriptions-item>
        <el-descriptions-item label="最近更新">{{ timeText(delivery.updatedAt) }}</el-descriptions-item>
        <el-descriptions-item label="异常说明" :span="2">{{ delivery.exceptionNote || '暂无' }}</el-descriptions-item>
      </el-descriptions>
      <div class="buttons">
        <el-button v-if="can('assign')" type="primary" @click="editAssignment">安排交付 / 调整负责人</el-button>
        <el-button v-if="can('production')" @click="editProgress('PRODUCTION')">更新生产情况</el-button>
        <el-button v-if="can('stock')" @click="editProgress('STOCKING')">更新备货情况</el-button>
        <el-button v-if="can('ship') && ['STOCKING','PARTIAL'].includes(delivery.status)" type="success" @click="shipmentForm.open(delivery)">登记实际发货</el-button>
        <el-button v-if="can('complete') && delivery.status==='SHIPPED'" type="success" @click="finish">确认客户收货 / 完成交付</el-button>
      </div>
      <el-alert v-if="delivery.started && delivery.orderStatus==='CONFIRMED'" title="已开始执行：订单变更或取消前，需当前生产、仓储负责人确认执行影响。确认仅对当前订单版本有效。" type="info" :closable="false" />
      <div v-if="delivery.started" class="buttons">
        <span v-if="delivery.productionId">生产影响确认：{{ delivery.productionAck===delivery.orderVersion?'已确认当前版本':'待确认' }}</span>
        <el-button v-if="can('ackProduction')" link type="primary" @click="ack('PRODUCTION')">确认生产执行影响</el-button>
        <span v-if="delivery.warehouseId">仓储影响确认：{{ delivery.warehouseAck===delivery.orderVersion?'已确认当前版本':'待确认' }}</span>
        <el-button v-if="can('ackStock')" link type="primary" @click="ack('STOCKING')">确认仓储执行影响</el-button>
      </div>
      <h3>订单产品与发货汇总</h3>
      <el-table :data="delivery.items"><el-table-column label="产品 / SKU" min-width="220"><template #default="{row}"><strong>{{ row.productName }}</strong><div>{{ row.skuCode }}</div><small>{{ row.specification }}</small></template></el-table-column><el-table-column prop="requirements" label="定制 / 包装要求" min-width="180" /><el-table-column prop="quantity" label="订购" width="85" /><el-table-column prop="shippedQuantity" label="已发" width="85" /><el-table-column label="剩余待发" width="110"><template #default="{row}">{{ row.quantity-row.shippedQuantity }} {{ row.unit }}</template></el-table-column></el-table>
      <h3>发货记录</h3>
      <el-table :data="delivery.shipments"><el-table-column prop="shipmentNo" label="发货单号" min-width="200" /><el-table-column prop="carrier" label="物流公司" /><el-table-column prop="trackingNo" label="单号" min-width="180" /><el-table-column label="状态"><template #default="{row}">{{ row.status==='POSTED'?'有效':'已更正作废' }}</template></el-table-column><el-table-column label="操作" width="80"><template #default="{row}"><el-button link type="primary" @click="shipmentDetail.open(row.id)">详情</el-button></template></el-table-column></el-table>
      <h3>交付操作记录</h3>
      <el-timeline><el-timeline-item v-for="event in delivery.events" :key="event.id" :timestamp="timeText(event.createdAt)"><strong>{{ deliveryEvents[event.action] }} · {{ event.actorName }}</strong><p>{{ event.note }}</p></el-timeline-item></el-timeline>
    </template>
    <el-dialog v-model="editing" :title="assigning?'安排订单交付':'更新交付进度'" width="min(650px,95vw)" append-to-body destroy-on-close>
      <el-form label-width="120px">
        <template v-if="assigning">
          <el-form-item v-for="item in peopleFields" :key="item.key" :label="item.label"><el-select v-model="form[item.key]" filterable clearable><el-option v-for="person in people[item.kind]" :key="person.id" :label="person.name" :value="person.id" /></el-select></el-form-item>
        </template>
        <el-form-item v-else label="执行环节">{{ deliveryStates[form.preparation] }}</el-form-item>
        <el-form-item label="预计发货日期"><el-date-picker v-model="form.expectedDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="异常 / 情况说明"><el-input v-model="form.exceptionNote" type="textarea" maxlength="1000" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="editing=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
    <ShipmentForm ref="shipmentForm" @saved="changed" /><ShipmentDetail ref="shipmentDetail" @changed="changed" />
  </el-drawer>
</template>
<script setup>
import {ref} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import {getDelivery,deliveryPeople,assignDelivery,updateDelivery,acknowledgeDelivery,completeDelivery} from '@/api/delivery'
import {deliveryStates,deliveryEvents} from '@/config/delivery'
import {timeText} from '@/config/workflow'
import ShipmentForm from './ShipmentForm.vue'
import ShipmentDetail from './ShipmentDetail.vue'
const emit=defineEmits(['changed'])
const visible=ref(false),delivery=ref(null),editing=ref(false),assigning=ref(false),form=ref({}),people=ref({}),saving=ref(false),shipmentForm=ref(),shipmentDetail=ref()
const peopleFields=[{key:'managerId',kind:'manager',label:'交付对接经理'},{key:'productionId',kind:'production',label:'生产负责人'},{key:'warehouseId',kind:'warehouse',label:'备货发货负责人'}]
const can=action=>delivery.value.actions.includes(action)
async function open(id) {delivery.value=await getDelivery(id);visible.value=true}
async function changed() {await open(delivery.value.orderId);emit('changed')}
async function editAssignment() {
  people.value=await deliveryPeople(delivery.value.orderId)
  form.value={version:delivery.value.version,managerId:delivery.value.managerId,productionId:delivery.value.productionId,warehouseId:delivery.value.warehouseId,expectedDate:delivery.value.expectedDate,exceptionNote:delivery.value.exceptionNote}
  assigning.value=true;editing.value=true
}
function editProgress(preparation) {form.value={version:delivery.value.version,preparation,expectedDate:delivery.value.expectedDate,exceptionNote:delivery.value.exceptionNote};assigning.value=false;editing.value=true}
async function save() {
  if(assigning.value && !form.value.managerId) {ElMessage.warning('请选择交付对接经理');return}
  saving.value=true
  try {if(assigning.value)await assignDelivery(delivery.value.orderId,form.value);else await updateDelivery(delivery.value.orderId,form.value);editing.value=false;ElMessage.success('交付信息已更新');await changed()} finally {saving.value=false}
}
async function note(title,prompt) {
  try {return (await ElMessageBox.prompt(prompt,title,{inputType:'textarea',inputValidator:value=>!!value?.trim() && value.length<=500 || '请填写1-500字说明'})).value} catch {return null}
}
async function ack(kind) {
  const text=await note('确认订单调整执行影响','请与经理核对拟调整内容，记录已确认的生产或备货影响；这不是确认客户下单。')
  if(!text)return
  await acknowledgeDelivery(delivery.value.orderId,{orderVersion:delivery.value.orderVersion,kind,note:text});ElMessage.success('已记录当前订单版本的执行影响确认');await changed()
}
async function finish() {
  const text=await note('确认交付完成','请确认客户已收到全部商品，填写签收或收货确认依据。')
  if(!text)return
  await completeDelivery(delivery.value.orderId,{version:delivery.value.version,note:text});ElMessage.success('交付已完成，销售订单同步完成');await changed()
}
defineExpose({open})
</script>
<style scoped>.buttons{display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin:18px 0}.buttons .el-button{margin:0}small,p{white-space:pre-wrap}small{color:#758196}h3{margin-top:25px}</style>
