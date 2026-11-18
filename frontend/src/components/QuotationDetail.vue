<template>
  <el-drawer v-model="visible" title="报价详情与版本" size="min(1100px,96vw)" append-to-body>
    <template v-if="quote">
      <div class="quote-heading"><div><h2>{{ quote.quoteNo }} · V{{ quote.versionNo }}</h2><el-tag>{{ quotationStatuses[quote.displayStatus] }}</el-tag> <el-tag v-if="quote.isFinal" type="success">该商机最终报价</el-tag></div><el-button type="primary" plain @click="print">打印 / 另存为PDF</el-button></div>
      <p class="text-sub">历史内容为报价快照；客户和商机归属保持关联。打印后可在浏览器中选择“另存为PDF”。</p>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="标题">{{ quote.title }}</el-descriptions-item><el-descriptions-item label="商机">{{ quote.opportunityTitle }}</el-descriptions-item>
        <el-descriptions-item label="报价客户">{{ quote.customerName }}</el-descriptions-item><el-descriptions-item label="当前客户 / 负责人">{{ quote.currentCustomerName }} / {{ quote.ownerName }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ quote.contactName }}</el-descriptions-item><el-descriptions-item label="手机 / 座机">{{ quote.contactPhone || '—' }} / {{ quote.contactLandline || '—' }}</el-descriptions-item>
        <el-descriptions-item label="微信 / 邮箱">{{ quote.contactWechat || '—' }} / {{ quote.contactEmail || '—' }}</el-descriptions-item><el-descriptions-item label="报价经办人">{{ quote.sellerName }}</el-descriptions-item>
        <el-descriptions-item label="报价日期">{{ quote.quoteDate }}</el-descriptions-item><el-descriptions-item label="有效期至">{{ quote.validUntil }}</el-descriptions-item>
        <el-descriptions-item label="付款条件">{{ quote.paymentTerms }}</el-descriptions-item><el-descriptions-item label="交期约定">{{ quote.deliveryTerms }}</el-descriptions-item>
        <el-descriptions-item label="运费约定">{{ quote.freightTerms || '—' }}</el-descriptions-item><el-descriptions-item label="金额口径">人民币 · {{ quote.taxMode==='INCLUDED'?'含税':'未税' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ quote.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="quote.items"><el-table-column label="产品 / SKU与规格" min-width="280"><template #default="{row}"><strong>{{ row.productName }}</strong><div>{{ row.modelCode }} / {{ row.skuCode }}</div><p class="spec">{{ row.specification || '未记录' }}</p><p v-if="row.requirements" class="spec">特殊要求：{{ row.requirements }}</p></template></el-table-column><el-table-column label="数量" width="100"><template #default="{row}">{{ row.quantity }} {{ row.unit }}</template></el-table-column><el-table-column label="单价" width="130"><template #default="{row}">{{ money(row.unitPrice) }}</template></el-table-column><el-table-column label="金额" width="150"><template #default="{row}">{{ money(row.lineAmount) }}</template></el-table-column></el-table>
      <p class="total">合计：CNY {{ money(quote.totalAmount) }}</p>
      <h3>同一报价的版本</h3><div class="versions"><el-button v-for="version in quote.versions" :key="version.id" :type="version.id===quote.id?'primary':'default'" plain @click="open(version.id)">V{{ version.versionNo }} · {{ quotationStatuses[version.status] }}{{ version.isFinal?' · 最终':'' }}</el-button></div>
      <h3>处理记录</h3><el-table :data="quote.events"><el-table-column label="时间" width="180"><template #default="{row}">{{ timeText(row.createdAt) }}</template></el-table-column><el-table-column label="操作" width="130"><template #default="{row}">{{ quotationEvents[row.action] || row.action }}</template></el-table-column><el-table-column prop="actorName" label="经办人" width="110" /><el-table-column prop="note" label="说明" min-width="240" /></el-table>
    </template>
  </el-drawer>
  <Teleport to="body"><section v-if="printQuote" class="quotation-print">
    <h1>{{ companyName }}</h1><h2>产品报价单</h2><p>{{ printQuote.quoteNo }} · V{{ printQuote.versionNo }} · {{ quotationStatuses[printQuote.displayStatus] }}{{ printQuote.isFinal?' · 最终报价':'' }}</p>
    <p>客户：{{ printQuote.customerName }}　联系人：{{ printQuote.contactName }}</p><p>手机 / 座机：{{ printQuote.contactPhone || '—' }} / {{ printQuote.contactLandline || '—' }}</p><p>微信：{{ printQuote.contactWechat || '—' }}　邮箱：{{ printQuote.contactEmail || '—' }}</p>
    <p>报价日期：{{ printQuote.quoteDate }}　有效期至：{{ printQuote.validUntil }}</p>
    <table><thead><tr><th>产品 / 型号 / SKU / 规格</th><th>数量</th><th>单价（元）</th><th>金额（元）</th></tr></thead><tbody><tr v-for="item in printQuote.items" :key="item.id"><td>{{ item.productName }}<br>{{ item.modelCode }} / {{ item.skuCode }}<p>{{ item.specification }}</p><p v-if="item.requirements">特殊要求：{{ item.requirements }}</p></td><td>{{ item.quantity }} {{ item.unit }}</td><td>{{ money(item.unitPrice) }}</td><td>{{ money(item.lineAmount) }}</td></tr></tbody></table>
    <p class="print-total">人民币合计：¥ {{ money(printQuote.totalAmount) }}（{{ printQuote.taxMode==='INCLUDED'?'含税':'未税' }}）</p><p>付款条件：{{ printQuote.paymentTerms }}</p><p>交期约定：{{ printQuote.deliveryTerms }}</p><p>运费约定：{{ printQuote.freightTerms || '未单独约定' }}</p><p v-if="printQuote.remark">备注：{{ printQuote.remark }}</p><p>报价经办人：{{ printQuote.sellerName }}</p>
  </section></Teleport>
</template>
<script setup>
import {nextTick,onMounted,onUnmounted,ref} from 'vue'
import {getQuotation} from '@/api/quotation'
import {quotationStatuses,quotationEvents,money} from '@/config/quotation'
import {timeText} from '@/config/workflow'
import {companyName} from '@/config/company'
const visible=ref(false),quote=ref(null),printQuote=ref(null)
async function open(id){quote.value=await getQuotation(id);visible.value=true}
async function print(){printQuote.value=await getQuotation(quote.value.id);await nextTick();window.print()}
function afterPrint(){printQuote.value=null}
onMounted(()=>window.addEventListener('afterprint',afterPrint));onUnmounted(()=>window.removeEventListener('afterprint',afterPrint))
defineExpose({open})
</script>
<style scoped>.quote-heading{display:flex;align-items:center;justify-content:space-between;gap:15px}.quote-heading h2{font-size:20px}.spec{font-size:12px;color:#6b7787;white-space:pre-wrap}.total{text-align:right;font-size:20px;font-weight:600}.versions{display:flex;gap:10px;flex-wrap:wrap}.versions .el-button{margin:0}</style>
<style>
.quotation-print{display:none}
@media print {
  @page{size:A4;margin:14mm}
  body>*{display:none!important}
  body>.quotation-print{display:block!important;color:#000;background:white;font-size:11pt;line-height:1.5;font-family:'Microsoft YaHei',sans-serif}
  .quotation-print h1{text-align:center;font-size:19pt}.quotation-print h2{text-align:center;font-size:15pt}.quotation-print table{width:100%;border-collapse:collapse;table-layout:fixed}.quotation-print th,.quotation-print td{border:1px solid #555;padding:7px;overflow-wrap:anywhere;vertical-align:top}.quotation-print th:first-child{width:55%}.quotation-print td p{font-size:9pt;white-space:pre-wrap;margin:5px 0}.quotation-print thead{display:table-header-group}.quotation-print tr{break-inside:avoid}.quotation-print .print-total{text-align:right;font-size:13pt;font-weight:bold}
}
</style>
