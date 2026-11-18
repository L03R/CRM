<template>
  <el-drawer v-model="visible" :title="(kind==='LEAD'?'线索':'客户')+'详情'" size="min(950px,96vw)">
    <template v-if="detail">
      <div class="detail-title"><h3>{{ detail.companyName }}</h3><el-button @click="open(kind,detail.id)">刷新</el-button></div>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="客户类型">{{ customerTypes[detail.customerType] }}</el-descriptions-item>
        <el-descriptions-item label="地区">{{ detail.region }}</el-descriptions-item>
        <el-descriptions-item label="主要联系人">{{ detail.contactName || '未设置' }}</el-descriptions-item>
        <el-descriptions-item label="电话/邮箱">{{ detail.phone || '—' }} / {{ detail.email || '—' }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ detail.source }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.creatorName }}</el-descriptions-item>
        <el-descriptions-item label="当前负责人">{{ detail.ownerName || (kind==='LEAD'?'待分配':'客户公海') }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ kind==='LEAD'?leadStatuses[detail.status]:relationships[detail.relationship] }}</el-descriptions-item>
        <el-descriptions-item label="下次跟进">{{ timeText(detail.nextFollowAt) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ timeText(detail.createdAt) }}</el-descriptions-item>
        <el-descriptions-item v-if="kind==='LEAD'" label="意向销售部门">{{ detail.targetDeptName }}</el-descriptions-item>
        <el-descriptions-item v-if="kind==='LEAD'" label="关闭/无效原因">{{ detail.closeReason || '—' }}</el-descriptions-item>
        <el-descriptions-item v-if="kind==='CUSTOMER'" label="公海所属部门">{{ detail.poolDeptName }}</el-descriptions-item>
        <el-descriptions-item v-if="kind==='CUSTOMER'" label="未结束商机">{{ detail.activeOpportunities }}</el-descriptions-item>
        <el-descriptions-item v-if="kind==='CUSTOMER'" label="重要客户">{{ detail.protectedCustomer?'受保护':'否' }}</el-descriptions-item>
        <el-descriptions-item v-if="kind==='CUSTOMER'" label="原始线索">{{ detail.sourceLeadId ? '#'+detail.sourceLeadId : '直接建档' }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.sourceCreatorName" label="原线索录入人">{{ detail.sourceCreatorName }}</el-descriptions-item>
        <el-descriptions-item label="需求/备注" :span="2">{{ detail.demand || detail.remark || '—' }}</el-descriptions-item>
        <el-descriptions-item v-if="kind==='CUSTOMER'" label="地址" :span="2">{{ detail.address || '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-alert v-if="detail.convertedCustomerId" :title="'已转为客户 #'+detail.convertedCustomerId+'，后续沟通请进入客户档案。'" type="success" :closable="false" />
      <el-button v-if="kind==='CUSTOMER' && detail.ownerId && store.hasPerm('aftersale:add')" type="primary" plain @click="registerAfterSale">登记售后问题</el-button>
      <ContactWorkspace v-if="kind==='CUSTOMER' && store.hasPerm('contact:list')" :key="detail.id" :customer="detail" @changed="contactsChanged" />
      <QuotationWorkspace v-if="kind==='CUSTOMER' && detail.ownerId && store.hasPerm('quotation:list') && store.hasPerm('business:list')" :key="'quotes'+detail.id" :customer="detail" />
      <h3>沟通历史（包含转客户前的线索记录）</h3>
      <el-table :data="follows" row-key="id">
        <el-table-column label="沟通时间" width="175"><template #default="{row}">{{ timeText(row.occurredAt) }}</template></el-table-column>
        <el-table-column prop="authorName" label="记录人" width="100" />
        <el-table-column label="联系对象" min-width="100"><template #default="{row}">{{ row.contactNameSnapshot || '未记录' }}</template></el-table-column>
        <el-table-column label="方式" width="75"><template #default="{row}">{{ followMethods[row.method] }}</template></el-table-column>
        <el-table-column prop="content" label="沟通内容" min-width="240" />
        <el-table-column label="阶段" width="85"><template #default="{row}">{{ row.resourceType==='LEAD'?'线索阶段':'客户阶段' }}</template></el-table-column>
      </el-table>
      <h3>归属及处理记录</h3>
      <el-table :data="events" row-key="id">
        <el-table-column label="时间" width="175"><template #default="{row}">{{ timeText(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="160"><template #default="{row}">{{ ownershipActions[row.action] || row.action }}</template></el-table-column>
        <el-table-column prop="actorName" label="操作人" width="100" />
        <el-table-column label="负责人变化" min-width="160"><template #default="{row}">{{ row.fromName || '未指定' }} → {{ row.toName || '待分配/公海' }}</template></el-table-column>
        <el-table-column prop="reason" label="原因" min-width="200" />
      </el-table>
      <template v-if="kind==='CUSTOMER'">
        <h3>公海申请与审核</h3>
        <el-table :data="requests" row-key="id">
          <el-table-column prop="reason" label="申请原因" min-width="200" />
          <el-table-column label="状态" width="100"><template #default="{row}">{{ {PENDING:'待审核',APPROVED:'已通过',REJECTED:'已驳回'}[row.status] }}</template></el-table-column>
          <el-table-column prop="reviewNote" label="审核说明" min-width="200" />
          <el-table-column label="提交时间" width="175"><template #default="{row}">{{ timeText(row.createdAt) }}</template></el-table-column>
        </el-table>
      </template>
    </template>
  </el-drawer>
</template>
<script setup>
import {ref} from 'vue'
import {useRouter} from 'vue-router'
import {findMenuPath} from '@/utils/menu'
import {useUserStore} from '@/store/user'
import ContactWorkspace from './ContactWorkspace.vue'
import QuotationWorkspace from './QuotationWorkspace.vue'
import {getWorkflowDetail,workflowFollows,workflowEvents,customerPoolRequests} from '@/api/workflow'
import {customerTypes,leadStatuses,relationships,followMethods,ownershipActions,timeText} from '@/config/workflow'
const visible=ref(false), kind=ref(''), detail=ref(null), follows=ref([]), events=ref([]), requests=ref([])
const router=useRouter()
const store=useUserStore(),emit=defineEmits(['changed'])
function registerAfterSale(){const path=findMenuPath(store.menuTree,'delivery/after-sale');if(path){visible.value=false;router.push({path,query:{create:'1',customerId:detail.value.id,customerName:detail.value.companyName}})}}
async function contactsChanged(){await open(kind.value,detail.value.id);emit('changed')}
async function open(type,id){
  const item=await getWorkflowDetail(type,id)
  const [history,changes,approvals]=await Promise.all([workflowFollows(type,id),workflowEvents(type,id),type==='CUSTOMER'?customerPoolRequests(id):Promise.resolve([])])
  kind.value=type;detail.value=item;follows.value=history;events.value=changes;requests.value=approvals;visible.value=true
}
defineExpose({open})
</script>
<style scoped>.detail-title{display:flex;align-items:center;justify-content:space-between}</style>
