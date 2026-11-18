<template>
  <div class="page">
    <div class="page-header"><div><h2 class="page-title">{{ route.meta.title || title }}</h2><p class="page-desc">{{ description }}</p></div><el-tag type="info">待开发</el-tag></div>
    <div class="card">
      <el-alert title="当前已完成目录和权限入口，业务功能尚未开发，暂不支持录入或查询业务数据。" type="info" :closable="false" />
      <el-empty description="业务功能建设中" />
      <p class="text-sub">规划范围：{{ features.join('、') }}。</p>
    </div>
  </div>
</template>
<script setup>
import {useRoute} from 'vue-router'
defineProps({title:String,description:String,features:{type:Array,default:()=>[]}})
const route=useRoute()
</script>
