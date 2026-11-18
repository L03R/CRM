<template>
  <div class="page">
    <div class="page-header"><div><h2 class="page-title">操作日志</h2><p class="page-desc">查看员工、授权、产品与分类属性的成功变更记录</p></div></div>
    <div class="card">
      <div class="toolbar"><el-input v-model="query.keyword" placeholder="工号 / 姓名 / 操作" clearable style="width:280px" @keyup.enter="load(true)" /><el-button type="primary" @click="load(true)">查询</el-button></div>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column prop="createdAt" label="操作时间" width="185" />
        <el-table-column prop="employeeNo" label="工号" width="145" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="operation" label="操作" min-width="160" />
        <el-table-column prop="resource" label="操作对象（接口及记录ID）" min-width="300" />
      </el-table>
      <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="load(true)" />
      <p class="text-sub">只记录成功的登录后变更操作，不记录请求正文、密码或令牌；日志不提供修改和删除入口。</p>
    </div>
  </div>
</template>
<script setup>
import {onMounted,reactive,ref} from 'vue'
import request from '@/api/request'
const query=reactive({keyword:'',page:1,size:20}), rows=ref([]), total=ref(0), loading=ref(false)
async function load(reset=false){
  if(reset===true)query.page=1
  loading.value=true
  try{const result=await request.get('/system/logs',{params:query});rows.value=result.records;total.value=result.total}
  finally{loading.value=false}
}
onMounted(load)
</script>
