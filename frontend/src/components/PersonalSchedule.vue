<template>
  <div class="card">
    <div class="section-head"><div><h3>我的安排</h3><p class="text-sub">个人日程只对本人可见；业务跟进计划沿用业务数据范围。</p></div><el-button type="primary" plain @click="open()">新增日程</el-button></div>
    <div class="schedule-tools"><el-radio-group v-model="mode" @change="load"><el-radio-button value="calendar">日历视图</el-radio-button><el-radio-button value="list">列表视图</el-radio-button></el-radio-group><el-date-picker v-if="mode==='list'" v-model="range" type="daterange" value-format="YYYY-MM-DD" @change="load" /><el-checkbox v-model="showEnded">显示已完成 / 已取消</el-checkbox><el-button @click="load">刷新</el-button></div>
    <p class="text-sub">到点提醒仅在打开本页时显示。业务计划请进入对应线索或客户修改，个人日程完成不会结束业务跟进。</p>
    <el-calendar v-if="mode==='calendar'" v-model="calendarDate">
      <template #date-cell="{data}"><div class="calendar-day"><span>{{ data.day.slice(-2) }}</span><span v-if="dayCount(data.day)" class="event-count">{{ dayCount(data.day) }}项</span><div v-for="item in dayItems(data.day).slice(0,2)" :key="item.key" class="calendar-event">{{ item.title }}</div></div></template>
    </el-calendar>
    <h4>{{ mode==='calendar'?selectedDay+' · 个人日程':'个人日程' }}</h4>
    <el-table v-loading="loading" :data="visibleRows">
      <el-table-column label="日程" min-width="170"><template #default="{row}"><el-tag v-if="row.priority==='HIGH'" type="danger" size="small">重要</el-tag> {{ row.title }}<el-tag v-if="reminderDue(row)" size="small" type="warning">到点提醒</el-tag></template></el-table-column>
      <el-table-column label="开始时间" width="175"><template #default="{row}">{{ timeText(row.startAt) }}</template></el-table-column>
      <el-table-column label="状态" width="85"><template #default="{row}">{{ states[row.status] }}</template></el-table-column>
      <el-table-column label="操作" width="130"><template #default="{row}"><el-button link type="primary" @click="open(row)">编辑</el-button><el-button v-if="row.status==='PENDING'" link type="success" @click="finish(row)">完成</el-button></template></el-table-column>
    </el-table>
    <h4>{{ mode==='calendar'?selectedDay+' · 业务跟进计划':'业务跟进计划' }}</h4>
    <el-table v-loading="loading" :data="visibleAgenda"><el-table-column prop="title" label="线索 / 客户 / 售后问题" min-width="230" /><el-table-column prop="reason" label="类型" width="120" /><el-table-column label="跟进时间" width="180"><template #default="{row}">{{ timeText(row.dueAt) }}</template></el-table-column><el-table-column label="操作" width="150"><template #default="{row}"><el-button link type="primary" @click="showDetail(row)">详情</el-button><el-button link type="primary" @click="process(row)">去处理</el-button></template></el-table-column></el-table>
    <WorkflowDetail ref="detail" />
    <el-dialog v-model="visible" :title="form.id?'编辑日程':'新增日程'" width="550px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="90px">
        <el-form-item label="日程标题" prop="title" :rules="[{required:true,whitespace:true,message:'请填写标题'}]"><el-input v-model.trim="form.title" maxlength="128" /></el-form-item>
        <el-form-item label="开始时间" prop="startAt" :rules="[{required:true,message:'请选择开始时间'}]"><el-date-picker v-model="form.startAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="结束时间" prop="endAt" :rules="[{required:true,message:'请选择结束时间'}]"><el-date-picker v-model="form.endAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="提醒时间"><el-date-picker v-model="form.remindAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" clearable /></el-form-item>
        <el-form-item label="优先级"><el-radio-group v-model="form.priority"><el-radio value="NORMAL">普通</el-radio><el-radio value="HIGH">重要</el-radio></el-radio-group></el-form-item>
        <el-form-item label="状态"><el-select v-model="form.status"><el-option v-for="(name,key) in states" :key="key" :label="name" :value="key" /></el-select></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" maxlength="1000" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup>
import {computed,onMounted,onUnmounted,ref,watch} from 'vue'
import {useRouter} from 'vue-router'
import {useUserStore} from '@/store/user'
import {ElMessage} from 'element-plus'
import {getSchedules,saveSchedule,getAgenda} from '@/api/dashboard'
import {localTime,timeText} from '@/config/workflow'
import {workItemLocation} from '@/utils/workItem'
import WorkflowDetail from './WorkflowDetail.vue'
const states={PENDING:'待完成',DONE:'已完成',CANCELLED:'已取消'}
const now=ref(Date.now()),timer=setInterval(()=>now.value=Date.now(),30000)
const start=new Date(),end=new Date();end.setDate(end.getDate()+6)
const range=ref([localTime(start).slice(0,10),localTime(end).slice(0,10)])
const mode=ref('calendar'),calendarDate=ref(new Date()),agenda=ref([]),detail=ref(),router=useRouter(),store=useUserStore()
const selectedDay=computed(()=>localTime(calendarDate.value).slice(0,10))
const rows=ref([]),loading=ref(false),saving=ref(false),visible=ref(false),showEnded=ref(false),form=ref({}),formRef=ref()
const pendingRows=computed(()=>rows.value.filter(r=>showEnded.value || r.status==='PENDING'))
const onDay=(row,day)=>row.startAt.slice(0,10)<=day && row.endAt.slice(0,10)>=day
const visibleRows=computed(()=>pendingRows.value.filter(r=>mode.value==='list' || onDay(r,selectedDay.value)))
const visibleAgenda=computed(()=>agenda.value.filter(r=>mode.value==='list' || r.dueAt.slice(0,10)===selectedDay.value))
function dayItems(day){return [...pendingRows.value.filter(r=>onDay(r,day)).map(r=>({key:'schedule'+r.id,title:r.title})),...agenda.value.filter(r=>r.dueAt.slice(0,10)===day).map(r=>({key:r.kind+r.id,title:r.title}))]}
const dayCount=day=>dayItems(day).length
const reminderDue=row=>row.status==='PENDING' && row.remindAt && new Date(row.remindAt).getTime()<=now.value
function dates(){if(mode.value==='list')return range.value;const date=calendarDate.value;return [localTime(new Date(date.getFullYear(),date.getMonth(),1-7)).slice(0,10),localTime(new Date(date.getFullYear(),date.getMonth()+1,7)).slice(0,10)]}
async function load(){const interval=dates();if(!interval)return;loading.value=true;try{const params={start:interval[0],end:interval[1]};const [personal,business]=await Promise.all([getSchedules(params),getAgenda(params)]);rows.value=personal;agenda.value=business}finally{loading.value=false}}
function open(row=null){const start=mode.value==='calendar'?selectedDay.value+'T09:00:00':localTime(),end=mode.value==='calendar'?selectedDay.value+'T10:00:00':localTime(new Date(Date.now()+3600000));form.value=row?{...row}:{title:'',description:'',startAt:start,endAt:end,remindAt:null,priority:'NORMAL',status:'PENDING'};visible.value=true}
function process(row){const target=workItemLocation(store.menuTree,row);if(target)router.push(target)}
function showDetail(row){if(row.kind==='AFTERSALE')process(row);else detail.value.open(row.kind,row.id)}
watch(()=>calendarDate.value.getFullYear()+'-'+calendarDate.value.getMonth(),()=>{if(mode.value==='calendar')load()})
async function submit(){try{await formRef.value.validate()}catch{return}saving.value=true;try{await saveSchedule(form.value);visible.value=false;ElMessage.success('日程已保存');await load()}finally{saving.value=false}}
async function finish(row){await saveSchedule({...row,status:'DONE'});ElMessage.success('日程已完成');await load()}
onMounted(load);onUnmounted(()=>clearInterval(timer))
</script>
<style scoped>
.card{padding:20px}
.calendar-day{font-size:13px;min-width:0}.event-count{float:right;color:#5368df;font-size:11px}.calendar-event{margin-top:5px;font-size:11px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#60708f}.card :deep(.el-calendar-day){height:100px}.card :deep(.el-calendar__body){padding:10px 0}h4{font-size:14px;margin:24px 0 12px}
.section-head{display:flex;align-items:center;justify-content:space-between;gap:12px}.section-head h3{margin:0;font-size:16px}.schedule-tools{display:flex;align-items:center;gap:15px;flex-wrap:wrap;margin:15px 0}
</style>
