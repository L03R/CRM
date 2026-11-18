<template>
  <div class="page">
    <div class="page-header"><div><h2 class="page-title">跟进记录</h2><p class="page-desc">查询有权访问客户的沟通历史，包含转客户前的线索沟通。</p></div></div>
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="客户名称 / 沟通内容" clearable style="width:260px" @keyup.enter="load(true)" />
        <el-select v-model="query.due" style="width:190px"><el-option label="全部客户记录" value="ALL" /><el-option label="今日计划跟进的客户" value="TODAY" /><el-option label="计划已逾期的客户" value="OVERDUE" /></el-select>
        <el-button type="primary" @click="load(true)">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column prop="companyName" label="客户" min-width="220" />
        <el-table-column label="沟通时间" width="180"><template #default="{row}">{{ timeText(row.occurredAt) }}</template></el-table-column>
        <el-table-column label="联系对象" min-width="110"><template #default="{row}">{{ row.contactNameSnapshot || '未记录' }}</template></el-table-column>
        <el-table-column prop="authorName" label="记录人" width="110" />
        <el-table-column label="方式" width="85"><template #default="{row}">{{ followMethods[row.method] }}</template></el-table-column>
        <el-table-column prop="content" label="沟通内容" min-width="280" />
        <el-table-column label="阶段" width="100"><template #default="{row}">{{ row.resourceType==='LEAD'?'线索阶段':'客户阶段' }}</template></el-table-column>
        <el-table-column label="客户当前计划" width="180"><template #default="{row}">{{ timeText(row.nextFollowAt) }}</template></el-table-column>
        <el-table-column v-if="store.hasPerm('customer:list')" label="操作" width="90"><template #default="{row}"><el-button link type="primary" @click="detailPanel.open('CUSTOMER',row.customerId)">客户详情</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="load(true)" />
    </div>
    <WorkflowDetail ref="detailPanel" />
  </div>
</template>
<script setup>
import {onMounted,reactive,ref} from 'vue'
import {useUserStore} from '@/store/user'
import {pageFollows} from '@/api/workflow'
import {followMethods,timeText} from '@/config/workflow'
import WorkflowDetail from '@/components/WorkflowDetail.vue'
const store=useUserStore(), query=reactive({keyword:'',due:'ALL',page:1,size:20}), rows=ref([]), total=ref(0), loading=ref(false), detailPanel=ref()
async function load(reset=false){if(reset===true)query.page=1;loading.value=true;try{const result=await pageFollows(query);rows.value=result.records;total.value=result.total}finally{loading.value=false}}
onMounted(load)
</script>
