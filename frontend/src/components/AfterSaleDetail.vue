<template>
  <el-drawer v-model="visible" title="售后问题与交接记录" size="min(1100px,96vw)" append-to-body @closed="releaseImages">
    <template v-if="record">
      <h2>{{ record.caseNo }} <el-tag>{{ afterSaleStates[record.status] }}</el-tag> <el-tag v-if="record.sourcePending" type="warning">来源待核实</el-tag></h2>
      <h3>{{ record.title }}</h3>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="客户">{{ record.customerName }}</el-descriptions-item><el-descriptions-item label="问题类型">{{ afterSaleTypes[record.issueType] }}</el-descriptions-item>
        <el-descriptions-item label="来源订单">{{ record.orderNo || '尚未明确' }}</el-descriptions-item><el-descriptions-item label="发货批次">{{ record.shipmentNo || '未关联' }}</el-descriptions-item>
        <el-descriptions-item label="反馈人 / 联系方式">{{ record.reporterName }} / {{ record.reporterContact }}</el-descriptions-item><el-descriptions-item label="反馈时间">{{ timeText(record.reportedAt) }}</el-descriptions-item>
        <el-descriptions-item label="登记人">{{ record.creatorName }}</el-descriptions-item><el-descriptions-item label="跟进负责人">{{ record.ownerName }}</el-descriptions-item>
        <el-descriptions-item label="客户问题描述" :span="2">{{ record.description }}</el-descriptions-item><el-descriptions-item label="客户期望">{{ record.expectation || '—' }}</el-descriptions-item><el-descriptions-item label="序列号 / 批次">{{ record.serialNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="转交对象 / 联系方式">{{ record.handlerName || '待确定' }} / {{ record.handlerContact || '—' }}</el-descriptions-item><el-descriptions-item label="内部协助人">{{ record.helperName || '无' }}</el-descriptions-item>
        <el-descriptions-item label="最近反馈" :span="2">{{ record.latestFeedback || '暂无' }}</el-descriptions-item><el-descriptions-item label="下次跟进">{{ timeText(record.nextFollowAt) }}</el-descriptions-item><el-descriptions-item label="最近更新">{{ timeText(record.updatedAt) }}</el-descriptions-item>
      </el-descriptions>
      <div class="buttons"><el-button v-if="can('source')" @click="sourceForm.open({},record)">核实来源</el-button><el-button v-if="can('transfer')" type="primary" @click="edit('transfer')">转交 / 调整对接人</el-button><el-button v-if="can('follow')" @click="edit('follow')">记录反馈 / 设置跟进</el-button><el-button v-if="can('assist')" @click="edit('assist')">记录协助反馈</el-button><el-button v-if="can('assign')" @click="edit('owner')">调整跟进负责人</el-button><el-button v-if="can('close')" type="success" @click="edit('close')">记录结果并结束</el-button><el-button v-if="can('reopen')" type="warning" @click="edit('reopen')">重新开启</el-button></div>
      <el-alert title="已转交不等于已解决。维修、退货、补发和退款在其他流程执行，这里只留存反馈、结果及外部单号。" type="info" :closable="false" />
      <h3>涉及产品（登记 / 核实时的快照）</h3>
      <el-table :data="record.items"><el-table-column prop="productName" label="产品" min-width="180" /><el-table-column prop="skuCode" label="SKU / 型号" min-width="170" /><el-table-column prop="specification" label="规格 / 情况" min-width="250" /><el-table-column prop="quantity" label="反馈数量" width="100" /></el-table>
      <h3>问题图片</h3><div class="images"><el-image v-for="image in images" :key="image.id" :src="image.url" :preview-src-list="images.map(item=>item.url)" fit="cover" preview-teleported /><el-upload v-if="can('follow') || can('assist')" :http-request="upload" :before-upload="beforeUpload" :show-file-list="false" accept="image/png,image/jpeg" :disabled="saving"><el-button>补充图片</el-button><template #tip><small>PNG/JPEG，每张5MB以内，最多8张</small></template></el-upload></div>
      <template v-if="record.resultNote"><h3>最近一次处理结果</h3><el-descriptions :column="2" border><el-descriptions-item label="处理方式">{{ afterSaleResults[record.resolutionType] }}</el-descriptions-item><el-descriptions-item label="外部单号">{{ record.externalNo || '—' }}</el-descriptions-item><el-descriptions-item label="结果说明" :span="2">{{ record.resultNote }}</el-descriptions-item><el-descriptions-item label="客户反馈 / 结束依据" :span="2">{{ record.customerFeedback }}</el-descriptions-item><el-descriptions-item label="结束时间">{{ timeText(record.closedAt) }}</el-descriptions-item></el-descriptions></template>
      <h3>登记、转交与跟进历史</h3><el-timeline><el-timeline-item v-for="event in record.events" :key="event.id" :timestamp="timeText(event.occurredAt)"><strong>{{ afterSaleEvents[event.action] }} · {{ event.actorName }}</strong><p>{{ event.content }}</p><small v-if="event.occurredAt!==event.createdAt">录入时间：{{ timeText(event.createdAt) }}</small></el-timeline-item></el-timeline>
    </template>
    <el-dialog v-model="editing" :title="titles[mode]" width="min(680px,95vw)" append-to-body destroy-on-close>
      <el-form ref="actionForm" :model="form" :rules="rules" label-width="125px">
        <template v-if="mode==='transfer'"><el-form-item label="转交对象" prop="handlerName"><el-input v-model.trim="form.handlerName" maxlength="128" placeholder="内部部门对接人、维修点或供应商均可" /></el-form-item><el-form-item label="联系方式" prop="handlerContact"><el-input v-model.trim="form.handlerContact" maxlength="128" placeholder="电话、企业微信或邮箱等" /></el-form-item><el-form-item label="内部协助人"><el-select v-model="form.helperId" clearable filterable><el-option v-for="person in people.helpers" :key="person.id" :label="person.name" :value="person.id" /></el-select></el-form-item></template>
        <el-form-item v-if="mode==='owner'" label="跟进负责人" prop="ownerId"><el-select v-model="form.ownerId" filterable><el-option v-for="person in people.owners" :key="person.id" :label="person.name" :value="person.id" /></el-select></el-form-item>
        <template v-if="['follow','assist'].includes(mode)"><el-form-item label="实际反馈时间" prop="occurredAt"><el-date-picker v-model="form.occurredAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item><el-form-item label="反馈内容" prop="content"><el-input v-model="form.content" type="textarea" :rows="4" maxlength="2000" /></el-form-item></template>
        <template v-if="mode==='close'"><el-form-item label="最终处理方式" prop="resolutionType"><el-select v-model="form.resolutionType"><el-option v-for="(label,key) in afterSaleResults" :key="key" :label="label" :value="key" /></el-select></el-form-item><el-form-item label="处理结果" prop="resultNote"><el-input v-model="form.resultNote" type="textarea" maxlength="2000" /></el-form-item><el-form-item label="客户反馈 / 依据" prop="customerFeedback"><el-input v-model="form.customerFeedback" type="textarea" maxlength="1000" placeholder="记录客户反馈；无法继续处理时写明结束原因及已告知客户的情况" /></el-form-item><el-form-item label="外部单据编号"><el-input v-model="form.externalNo" maxlength="128" /></el-form-item></template>
        <el-form-item v-if="['transfer','owner','reopen'].includes(mode)" label="交接 / 调整说明" prop="note"><el-input v-model="form.note" type="textarea" :rows="3" :maxlength="mode==='transfer'?1000:500" /></el-form-item>
        <el-form-item v-if="['transfer','follow','reopen'].includes(mode)" label="下次跟进"><el-date-picker v-model="form.nextFollowAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
      </el-form><template #footer><el-button @click="editing=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存记录</el-button></template>
    </el-dialog>
    <AfterSaleForm ref="sourceForm" @saved="refresh" />
  </el-drawer>
</template>
<script setup>
import {computed,onBeforeUnmount,ref} from 'vue'
import {ElMessage} from 'element-plus'
import {getAfterSale,afterSalePeople,afterSaleAction,getAfterSaleImage,uploadAfterSaleImage} from '@/api/aftersale'
import {afterSaleStates,afterSaleTypes,afterSaleEvents,afterSaleResults} from '@/config/aftersale'
import {timeText,localTime} from '@/config/workflow'
import AfterSaleForm from './AfterSaleForm.vue'
const emit=defineEmits(['changed']),visible=ref(false),record=ref(null),images=ref([]),editing=ref(false),saving=ref(false),mode=ref(''),form=ref({}),people=ref({owners:[],helpers:[]}),actionForm=ref(),sourceForm=ref()
const titles={transfer:'转交或调整处理对接人',follow:'记录反馈与跟进计划',assist:'记录协助反馈',owner:'调整售后跟进负责人',close:'记录处理结果并结束',reopen:'重新开启售后问题'}
const required={transfer:['handlerName','handlerContact','note'],follow:['content','occurredAt'],assist:['content','occurredAt'],owner:['ownerId','note'],close:['resolutionType','resultNote','customerFeedback'],reopen:['note']}
const rules=computed(()=>Object.fromEntries((required[mode.value] || []).map(key=>[key,[{required:true,message:'请填写或选择此项'}]])))
const can=action=>record.value?.actions.includes(action)
function releaseImages(){images.value.forEach(item=>URL.revokeObjectURL(item.url));images.value=[]}
async function open(id) {
  record.value=await getAfterSale(id);releaseImages();visible.value=true
  const result=await Promise.allSettled(record.value.images.map(async item=>({...item,url:URL.createObjectURL(await getAfterSaleImage(id,item.id))})))
  images.value=result.filter(item=>item.status==='fulfilled').map(item=>item.value)
}
async function refresh(){await open(record.value.id);emit('changed')}
async function edit(action) {
  mode.value=action
  form.value={version:record.value.version,handlerName:record.value.handlerName,handlerContact:record.value.handlerContact,helperId:record.value.helperId,ownerId:record.value.ownerId,note:'',content:'',occurredAt:localTime(),nextFollowAt:record.value.nextFollowAt,resolutionType:'OTHER',resultNote:'',customerFeedback:'',externalNo:''}
  if(['transfer','owner'].includes(action))people.value=await afterSalePeople(record.value.id)
  editing.value=true
}
async function save(){try{await actionForm.value.validate()}catch{return}saving.value=true;try{await afterSaleAction(record.value.id,mode.value,form.value);editing.value=false;ElMessage.success('售后记录已更新');await refresh()}finally{saving.value=false}}
function beforeUpload(file){if(!['image/png','image/jpeg'].includes(file.type) || file.size>5*1024*1024){ElMessage.warning('请选择5MB以内的PNG或JPEG图片');return false}return true}
async function upload({file}){saving.value=true;try{await uploadAfterSaleImage(record.value.id,file);await refresh();ElMessage.success('图片凭证已保存')}finally{saving.value=false}}
onBeforeUnmount(releaseImages)
defineExpose({open})
</script>
<style scoped>.buttons{display:flex;gap:10px;flex-wrap:wrap;margin:18px 0}.buttons .el-button{margin:0}.images{display:flex;gap:12px;flex-wrap:wrap}.images .el-image{width:110px;height:110px}h3{margin-top:22px}p,:deep(.el-descriptions__content){white-space:pre-wrap;overflow-wrap:anywhere}small{color:#758196}</style>
