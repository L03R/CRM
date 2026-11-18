<template>
  <el-drawer v-model="visible" title="销售订单详情" size="min(1150px,96vw)" append-to-body>
    <template v-if="order">
      <h2>{{ order.orderNo }} <el-tag>{{ orderStates[order.status] }}</el-tag></h2>
      <p>客户：{{ order.customerName }}　负责人：{{ order.ownerName }}　商机：{{ order.opportunityTitle }}</p>
      <el-alert title="草稿可编辑；已确认订单通过专门入口变更，需填写原因和客户确认依据，原报价及订单历史内容会保留。" type="info" :closable="false" />
      <el-alert v-if="order.executionStarted && order.status==='CONFIRMED'" title="订单已开始交付执行，变更或取消前请在交付详情中取得生产、仓储负责人的影响确认；已发货明细受保护。" type="warning" :closable="false" />
      <div class="buttons">
        <el-button v-if="store.hasPerm('aftersale:add') && ['CONFIRMED','COMPLETED'].includes(order.status)" @click="registerAfterSale">登记售后问题</el-button>
        <el-button v-if="order.deliveryStatus && deliveryPath" @click="showDelivery">查看交付进度</el-button>
        <el-button v-if="order.actions.includes('delete')" type="danger" :loading="saving" @click="remove">删除订单</el-button>
        <el-button v-if="order.actions.includes('edit')" type="primary" @click="form.open(order)">编辑草稿</el-button>
        <el-button v-if="order.actions.includes('confirm')" type="success" :loading="saving" @click="confirm">确认订单</el-button>
        <el-button v-if="order.actions.includes('change')" type="primary" @click="form.open(order,true)">变更订单</el-button>
        <el-button v-if="order.actions.includes('cancel') || order.actions.includes('cancel:confirmed')" type="warning" :loading="saving" @click="cancel">取消订单</el-button>
      </div>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="订单当前内容" name="content">
          <OrderContent :content="{order,items:order.items}" />
          <p v-if="order.confirmedAt">确认时间：{{ timeText(order.confirmedAt) }}</p>
          <p v-if="order.cancelReason">取消原因：{{ order.cancelReason }}</p>
        </el-tab-pane>
        <el-tab-pane label="来源报价" name="source">
          <h3>{{ order.quoteNo }} · V{{ order.versionNo }}</h3>
          <p>以下为生成订单时保留的原报价内容，订单变更不会改写原报价。</p>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="客户">{{ order.sourceSnapshot.quotation.customerName }}</el-descriptions-item>
            <el-descriptions-item label="联系人">{{ order.sourceSnapshot.quotation.contactName }}</el-descriptions-item>
            <el-descriptions-item label="原报价合计">{{ money(order.sourceSnapshot.quotation.totalAmount) }}</el-descriptions-item>
            <el-descriptions-item label="金额口径">{{ order.sourceSnapshot.quotation.taxMode==='INCLUDED'?'含税':'未税' }}</el-descriptions-item>
            <el-descriptions-item label="付款条件">{{ order.sourceSnapshot.quotation.paymentTerms }}</el-descriptions-item>
            <el-descriptions-item label="交期约定">{{ order.sourceSnapshot.quotation.deliveryTerms }}</el-descriptions-item>
          </el-descriptions>
          <el-table :data="order.sourceSnapshot.items"><el-table-column prop="productName" label="产品" min-width="160" /><el-table-column prop="skuCode" label="SKU" min-width="180" /><el-table-column prop="specification" label="规格" min-width="220" /><el-table-column prop="quantity" label="数量" width="85" /><el-table-column label="单价" width="110"><template #default="{row}">{{ money(row.unitPrice) }}</template></el-table-column><el-table-column label="金额" width="125"><template #default="{row}">{{ money(row.lineAmount) }}</template></el-table-column></el-table>
        </el-tab-pane>
        <el-tab-pane label="操作与变更历史" name="history">
          <el-empty v-if="!order.events.length" description="该订单暂没有历史记录" />
          <el-timeline v-else>
            <el-timeline-item v-for="event in order.events" :key="event.id" :timestamp="timeText(event.createdAt)" placement="top">
              <strong>{{ orderEvents[event.action] || event.action }} · {{ event.actorName }}</strong><p>{{ event.note }}</p>
              <el-collapse><el-collapse-item title="查看本次操作前后的内容">
                <template v-if="event.beforeContent"><h4>操作前</h4><OrderContent :content="event.beforeContent" /></template>
                <h4>操作后</h4><OrderContent :content="event.afterContent" />
              </el-collapse-item></el-collapse>
            </el-timeline-item>
          </el-timeline>
        </el-tab-pane>
      </el-tabs>
    </template>
    <OrderForm ref="form" @saved="changed" />
  </el-drawer>
</template>
<script setup>
import {ref,computed} from 'vue'
import {useRouter} from 'vue-router'
import {useUserStore} from '@/store/user'
import {findMenuPath} from '@/utils/menu'
import {ElMessage,ElMessageBox} from 'element-plus'
import {getOrder,confirmOrder,cancelOrder,cancelConfirmedOrder,deleteOrder} from '@/api/order'
import {money} from '@/config/quotation'
import {timeText} from '@/config/workflow'
import {orderStates,orderEvents} from '@/config/order'
import OrderContent from './OrderContent.vue'
import OrderForm from './OrderForm.vue'

const emit=defineEmits(['changed'])
const router=useRouter(),store=useUserStore()
const deliveryPath=computed(()=>findMenuPath(store.menuTree,'delivery/progress'))
function registerAfterSale(){const path=findMenuPath(store.menuTree,'delivery/after-sale');if(path){visible.value=false;router.push({path,query:{create:'1',customerId:order.value.customerId,customerName:order.value.customerName,orderId:order.value.id}})}}
function showDelivery(){visible.value=false;router.push({path:deliveryPath.value,query:{orderId:order.value.id}})}
const visible=ref(false),order=ref(null),saving=ref(false),form=ref(),activeTab=ref('content')
async function open(id) {
  order.value=await getOrder(id)
  activeTab.value='content'
  visible.value=true
}
async function changed(id) {
  await open(id)
  emit('changed')
}
async function remove() {
  try {await ElMessageBox.confirm('删除后订单从列表移除，原明细、取消原因和历史均保留。确认删除？','删除销售订单',{type:'warning',confirmButtonText:'删除'})} catch {return}
  saving.value=true
  try {await deleteOrder(order.value.id);visible.value=false;ElMessage.success('订单已删除');emit('changed')} finally {saving.value=false}
}
async function confirm() {
  const latest=await getOrder(order.value.id)
  order.value=latest
  const fields={recipientName:'收货人',recipientPhone:'收货电话',shippingAddress:'收货地址',promisedDate:'公司承诺交期',confirmationNote:'正式下单依据',paymentTerms:'付款条件',deliveryTerms:'交期约定'}
  const missing=Object.entries(fields).filter(([key])=>!String(latest[key] ?? '').trim()).map(([,label])=>label)
  if(missing.length) {
    const editable=latest.actions.includes('edit')
    try {
      await ElMessageBox.alert('还缺少：'+missing.join('、')+(editable?'。请补充并保存，再确认订单。':'。请联系有编辑权限的人员补充并保存。'),'订单信息未完整',{type:'warning',confirmButtonText:editable?'去补充':'知道了'})
      if(editable) await form.value.open(latest,false,true)
    } catch {return}
    return
  }
  try {await ElMessageBox.confirm('请核对当前产品、金额和交付要求，确保客户已正式确认。确认后，后续修改需要走订单变更入口。','确认正式订单',{type:'warning'})} catch {return}
  saving.value=true
  try {
    await confirmOrder(order.value.id)
    ElMessage.success('订单已确认，商机已标记为已赢单')
    await changed(order.value.id)
  } finally {saving.value=false}
}
async function cancel() {
  const confirmed=order.value.status==='CONFIRMED'
  let reason
  try {
    reason=(await ElMessageBox.prompt(confirmed?'取消后保留全部记录，商机恢复商务协商，可重新修订报价。请先确认业务无需继续执行。':'取消后保留草稿记录；如需重新下单，请修订报价生成新版本。','取消订单',{inputType:'textarea',inputValidator:value=>!!value?.trim() && value.trim().length<=500 || '请填写1-500字原因'})).value
  } catch {return}
  saving.value=true
  try {
    if(confirmed) await cancelConfirmedOrder(order.value.id,{reason,version:order.value.version})
    else await cancelOrder(order.value.id,reason)
    ElMessage.success('订单已取消，历史记录已保留')
    await changed(order.value.id)
  } finally {saving.value=false}
}
defineExpose({open})
</script>
<style scoped>.buttons{display:flex;gap:10px;flex-wrap:wrap;margin:20px 0}.buttons .el-button{margin:0}h4{margin:18px 0 12px}:deep(.el-timeline-item__content p){white-space:pre-wrap}</style>
