<template>
  <div class="card quotation-card">
    <div class="quote-toolbar"><h3>{{ customer?'客户报价':opportunity?'商机报价':'报价列表' }}</h3><el-button v-if="canCreate" type="primary" @click="form.open(null,context)">新建报价</el-button></div>
    <p class="text-sub">每份报价绑定客户和商机。同一商机可以有多份报价，但只能选定一份客户已接受的最终报价。</p>
    <div class="toolbar"><el-input v-model="query.keyword" placeholder="报价单号 / 标题 / 客户 / 商机" clearable style="width:260px" @keyup.enter="load(true)" /><el-select v-model="query.status" clearable placeholder="报价状态" style="width:140px" @change="load(true)"><el-option v-for="(name,key) in quotationStatuses" :key="key" :label="name" :value="key" /></el-select><el-checkbox v-model="query.onlyFinal" @change="load(true)">仅最终报价</el-checkbox><el-button @click="load(true)">查询</el-button></div>
    <el-table v-loading="loading" :data="rows" row-key="id">
      <el-table-column label="报价单号 / 标题" min-width="240"><template #default="{row}"><strong>{{ row.quoteNo }} · V{{ row.versionNo }}</strong><div>{{ row.title }}</div></template></el-table-column>
      <el-table-column v-if="!customer && !opportunity" prop="currentCustomerName" label="客户" min-width="200" />
      <el-table-column v-if="!opportunity" prop="opportunityTitle" label="关联商机" min-width="200" />
      <el-table-column label="金额（元）" width="145"><template #default="{row}">{{ money(row.totalAmount) }}</template></el-table-column>
      <el-table-column label="状态" width="115"><template #default="{row}"><el-tag :type="row.displayStatus==='ACCEPTED'?'success':row.displayStatus==='VOID'?'info':'primary'">{{ quotationStatuses[row.displayStatus] }}</el-tag></template></el-table-column>
      <el-table-column label="最终报价" width="105"><template #default="{row}"><el-tag v-if="row.isFinal" type="success">已选定</el-tag><span v-else>—</span></template></el-table-column>
      <el-table-column prop="validUntil" label="有效期至" width="120" /><el-table-column v-if="!customer" prop="ownerName" label="客户负责人" width="110" />
      <el-table-column label="操作" width="130" fixed="right"><template #default="{row}"><div class="quote-actions"><el-button link type="primary" @click="detail.open(row.id)">详情</el-button><el-dropdown v-if="row.actions.length" trigger="click" @command="command=>act(command,row)"><el-button link type="primary">更多</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="action in row.actions" :key="action" :command="action">{{ quotationActions[action] }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="load(true)" />
    <QuotationForm ref="form" @saved="load" /><QuotationDetail ref="detail" />
  </div>
</template>
<script setup>
import {computed,onMounted,reactive,ref} from 'vue'
import {useRouter} from 'vue-router'
import {findMenuPath} from '@/utils/menu'
import {generateOrder} from '@/api/order'
import {useUserStore} from '@/store/user'
import {ElMessage,ElMessageBox} from 'element-plus'
import {pageQuotations,reviseQuotation,transitionQuotation,deleteQuotation} from '@/api/quotation'
import {quotationStatuses,quotationActions,money} from '@/config/quotation'
import QuotationForm from './QuotationForm.vue'
import QuotationDetail from './QuotationDetail.vue'
const props=defineProps({customer:Object,opportunity:Object}),store=useUserStore()
const router=useRouter()
const query=reactive({keyword:'',status:null,onlyFinal:false,page:1,size:20}),rows=ref([]),total=ref(0),loading=ref(false),form=ref(),detail=ref()
const context=computed(()=>({customer:props.customer,opportunity:props.opportunity}))
const canCreate=computed(()=>store.hasPerm('product:list') && (props.customer?props.customer.actions.includes('quotationAdd'):props.opportunity?props.opportunity.actions.includes('quotationAdd'):store.hasPerm('quotation:add')))
async function load(reset=false){if(reset===true)query.page=1;loading.value=true;try{const result=await pageQuotations({...query,customerId:props.customer?.id || props.opportunity?.customerId,opportunityId:props.opportunity?.id});rows.value=result.records;total.value=result.total}finally{loading.value=false}}
const prompts={send:'请确认已将此版本发送给客户，并记录发送方式。',accept:'请记录客户接受本版报价的反馈或确认依据。接受报价不代表正式下单。',reject:'请记录客户未接受此版本的原因。',revise:'请填写修订原因，原版本和沟通历史会保留。',final:'请记录选定依据；将替换同一商机当前的最终报价。',void:'请填写作废原因，报价和历史明细会保留。'}
async function act(action,row){if(action==='delete'){
  try{await ElMessageBox.confirm('删除后此报价将从列表和版本选择中移除，原明细、作废原因和历史均保留，不影响历史订单快照。确认删除？','删除报价',{type:'warning',confirmButtonText:'删除'})}catch{return}
  await deleteQuotation(row.id);ElMessage.success('报价已删除');await load();return
}if(action==='order' || action==='orderView'){let id=row.orderId;if(action==='order'){try{await ElMessageBox.confirm('将此最终报价生成订单草稿，价格与配置保存快照。','生成订单',{type:'info'})}catch{return}id=await generateOrder(row.id);ElMessage.success('订单草稿已生成');await load()}const path=findMenuPath(store.menuTree,'order/index');if(path)router.push({path,query:{orderId:id}});return}if(action==='edit'){await form.value.open(row.id);return}let note;try{note=(await ElMessageBox.prompt(prompts[action],quotationActions[action],{inputType:'textarea',inputValidator:value=>!!value?.trim() && value.trim().length<=500 || '请填写1-500字说明'})).value}catch{return}if(action==='revise'){const id=await reviseQuotation(row.id,note);await load();await form.value.open(id);return}await transitionQuotation(row.id,action.toUpperCase(),note);ElMessage.success('报价已更新');await load()}
onMounted(load)
</script>
<style scoped>.quotation-card{padding:20px}.quote-toolbar{display:flex;align-items:center;justify-content:space-between;gap:15px}.quote-toolbar h3{font-size:16px;margin:0}.quote-actions{display:flex;align-items:center;gap:14px}.quote-actions .el-button{margin:0}</style>
