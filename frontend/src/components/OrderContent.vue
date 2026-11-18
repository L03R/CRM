<template>
  <div>
    <el-descriptions :column="2" border>
      <el-descriptions-item label="合计（人民币）">{{ money(content.order.totalAmount) }} · {{ content.order.taxMode==='INCLUDED'?'含税':'未税' }}</el-descriptions-item>
      <el-descriptions-item label="客户采购单号">{{ content.order.customerOrderNo || '—' }}</el-descriptions-item>
      <el-descriptions-item label="客户要求交期">{{ content.order.requestedDate || '—' }}</el-descriptions-item>
      <el-descriptions-item label="公司承诺交期">{{ content.order.promisedDate || '—' }}</el-descriptions-item>
      <el-descriptions-item label="收货人 / 电话">{{ content.order.recipientName || '—' }} / {{ content.order.recipientPhone || '—' }}</el-descriptions-item>
      <el-descriptions-item label="收货地址">{{ content.order.shippingAddress || '—' }}</el-descriptions-item>
      <el-descriptions-item label="付款条件">{{ content.order.paymentTerms || '—' }}</el-descriptions-item>
      <el-descriptions-item label="交期约定">{{ content.order.deliveryTerms || '—' }}</el-descriptions-item>
      <el-descriptions-item label="运费约定">{{ content.order.freightTerms || '—' }}</el-descriptions-item>
      <el-descriptions-item label="包装 / 交付备注">{{ content.order.remark || '—' }}</el-descriptions-item>
      <el-descriptions-item label="客户正式下单 / 变更确认依据" :span="2">{{ content.order.confirmationNote || '—' }}</el-descriptions-item>
    </el-descriptions>
    <el-table :data="content.items" class="order-items">
      <el-table-column label="产品 / SKU" min-width="220"><template #default="{row}"><strong>{{ row.productName }}</strong><div>{{ row.modelCode }} / {{ row.skuCode }}</div><small>{{ row.specification }}</small></template></el-table-column>
      <el-table-column label="数量" width="85"><template #default="{row}">{{ row.quantity }} {{ row.unit }}</template></el-table-column>
      <el-table-column label="单价" width="110"><template #default="{row}">{{ money(row.unitPrice) }}</template></el-table-column>
      <el-table-column label="金额" width="125"><template #default="{row}">{{ money(row.lineAmount) }}</template></el-table-column>
      <el-table-column prop="requirements" label="定制 / 包装要求" min-width="180" />
    </el-table>
  </div>
</template>
<script setup>
import {money} from '@/config/quotation'
defineProps({content:{type:Object,required:true}})
</script>
<style scoped>.order-items{margin-top:16px}small{white-space:pre-wrap;color:#758196}:deep(.el-descriptions__content){white-space:pre-wrap;overflow-wrap:anywhere}</style>
