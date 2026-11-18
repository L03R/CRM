<template>
  <div class="page">
    <div class="page-header"><div><h2 class="page-title">商机管理</h2><p class="page-desc">一条商机对应客户的一次具体采购需求；无当前商机的客户可继续定期维护。</p></div><el-button v-if="store.hasPerm('business:add')" type="primary" @click="dialog.open()">建立商机</el-button></div>
    <div class="card">
      <div class="toolbar"><el-input v-model="query.keyword" placeholder="商机名称 / 客户名称" clearable style="width:260px" @keyup.enter="load(true)" /><el-select v-model="query.stage" placeholder="全部阶段" clearable style="width:180px" @change="load(true)"><el-option v-for="(label,value) in opportunityStages" :key="value" :label="label" :value="value" /></el-select><el-button type="primary" @click="load(true)">查询</el-button><el-button @click="reset">重置</el-button></div>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column type="expand"><template #default="{row}"><el-descriptions :column="2"><el-descriptions-item label="采购需求">{{ row.remark || '—' }}</el-descriptions-item><el-descriptions-item label="结束说明">{{ row.closeReason || '—' }}</el-descriptions-item></el-descriptions></template></el-table-column>
        <el-table-column prop="title" label="商机名称" min-width="230" />
        <el-table-column prop="customerName" label="客户" min-width="210" />
        <el-table-column prop="ownerName" label="客户负责人" width="120" />
        <el-table-column label="阶段" width="150"><template #default="{row}"><el-tag :type="row.stage==='WON'?'success':row.stage==='LOST'?'info':'primary'">{{ opportunityStages[row.stage] }}</el-tag></template></el-table-column>
        <el-table-column label="预计金额" width="145"><template #default="{row}">{{ row.expectedAmount==null?'未确定':'¥ '+Number(row.expectedAmount).toFixed(2) }}</template></el-table-column>
        <el-table-column prop="expectedCloseDate" label="预计成交时间" width="150" />
        <el-table-column label="操作" width="235"><template #default="{row}"><el-button v-if="store.hasPerm('customer:list')" link type="primary" @click="detailPanel.open('CUSTOMER',row.customerId)">客户详情</el-button><el-button v-if="hasAction(row,'quotationView')" link type="primary" @click="selected=row;quotesVisible=true">报价</el-button><el-button v-if="hasAction(row,'edit')" link type="primary" @click="dialog.open(row)">编辑</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="load(true)" />
    </div>
    <OpportunityDialog ref="dialog" @saved="load" /><WorkflowDetail ref="detailPanel" />
    <el-drawer v-model="quotesVisible" title="商机报价" size="min(1300px,96vw)" destroy-on-close><QuotationWorkspace v-if="selected" :key="selected.id" :opportunity="selected" /></el-drawer>
  </div>
</template>
<script setup>
import {onMounted,reactive,ref} from 'vue'
import {useUserStore} from '@/store/user'
import {pageOpportunities} from '@/api/workflow'
import {opportunityStages,hasAction} from '@/config/workflow'
import OpportunityDialog from '@/components/OpportunityDialog.vue'
import WorkflowDetail from '@/components/WorkflowDetail.vue'
import QuotationWorkspace from '@/components/QuotationWorkspace.vue'
const store=useUserStore(), query=reactive({keyword:'',stage:null,page:1,size:20}), rows=ref([]), total=ref(0), loading=ref(false), dialog=ref(), detailPanel=ref()
const selected=ref(null),quotesVisible=ref(false)
async function load(reset=false){if(reset===true)query.page=1;loading.value=true;try{const result=await pageOpportunities(query);rows.value=result.records;total.value=result.total}finally{loading.value=false}}
function reset(){query.keyword='';query.stage=null;load(true)}
onMounted(load)
</script>
