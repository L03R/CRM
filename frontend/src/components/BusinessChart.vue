<template>
  <div class="business-chart">
    <el-empty v-if="!points.length" description="所选范围暂无数据" :image-size="65" />
    <div v-for="point in points" :key="point.name" class="chart-row">
      <div class="chart-label"><span>{{ labels[point.name] || point.name }}</span><strong>{{ point.value }}{{ unit }}</strong></div>
      <div class="track"><div class="fill" :style="{width:(point.value/max*100)+'%'}"></div></div>
    </div>
  </div>
</template>
<script setup>
import {computed} from 'vue'
const props=defineProps({points:{type:Array,default:()=>[]},labels:{type:Object,default:()=>({})},unit:{type:String,default:' 条'}})
const max=computed(()=>Math.max(1,...props.points.map(p=>p.value)))
</script>
<style scoped>
.business-chart{min-height:150px;max-height:380px;overflow:auto}.chart-row{margin:17px 0}.chart-label{display:flex;justify-content:space-between;gap:16px;font-size:13px;margin-bottom:7px}.chart-label span{overflow-wrap:anywhere}.chart-label strong{white-space:nowrap;font-variant-numeric:tabular-nums}.track{height:10px;background:#eef2f8;border-radius:6px;overflow:hidden}.fill{height:100%;background:linear-gradient(90deg,#5468ef,#8b9af9);border-radius:6px;min-width:2px}
</style>
