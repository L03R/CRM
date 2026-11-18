<template>
  <el-form-item label="公司名称" prop="companyName"><el-input v-model.trim="form.companyName" maxlength="128" /></el-form-item>
  <el-form-item label="客户类型" prop="customerType"><el-select v-model="form.customerType"><el-option v-for="(name,key) in customerTypes" :key="key" :label="name" :value="key" /></el-select></el-form-item>
  <el-form-item label="地区" prop="region"><el-input v-model.trim="form.region" maxlength="100" placeholder="如上海、广东广州" /></el-form-item>
  <el-form-item label="来源" prop="source"><el-input v-model.trim="form.source" maxlength="64" placeholder="展会 / 官网 / 主动开发 / 转介绍" /></el-form-item>
  <template v-if="lead || !form.id">
  <el-form-item label="主要联系人" prop="contactName"><el-input v-model.trim="form.contactName" maxlength="64" /></el-form-item>
  <el-form-item label="电话"><el-input v-model.trim="form.phone" maxlength="32" /></el-form-item>
  <el-form-item label="邮箱" prop="email"><el-input v-model.trim="form.email" maxlength="128" /></el-form-item>
  <p class="text-sub">电话和邮箱至少填写一项。保存前请核对公司名称，避免重复建档。</p>
  </template>
  <p v-if="!lead && form.id" class="text-sub">联系人在客户详情的“客户联系人”中维护，主要联系人自动同步展示。</p>
  <template v-if="lead">
    <el-form-item label="意向销售部门" prop="targetDeptId"><el-select v-model="form.targetDeptId" :disabled="!!form.ownerId" style="width:100%"><el-option v-for="dept in departments" :key="dept.id" :value="dept.id" :label="dept.deptName" /></el-select></el-form-item>
    <el-form-item label="初步需求" prop="demand"><el-input v-model="form.demand" type="textarea" :rows="3" maxlength="2000" /></el-form-item>
    <el-form-item label="下次跟进"><el-date-picker v-model="form.nextFollowAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" clearable /></el-form-item>
  </template>
  <template v-else>
    <el-form-item label="地址"><el-input v-model.trim="form.address" maxlength="255" /></el-form-item>
    <el-form-item label="合作状态"><el-select v-model="form.relationship"><el-option v-for="(name,key) in relationships" :key="key" :label="name" :value="key" /></el-select></el-form-item>
    <el-form-item label="重要客户"><el-switch v-model="form.protectedCustomer" :disabled="!canProtect" /><span class="text-sub">经理确认，受保护客户不能退回公海</span></el-form-item>
    <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="2000" /></el-form-item>
  </template>
</template>
<script setup>
import {customerTypes,relationships} from '@/config/workflow'
defineProps({form:Object,lead:Boolean,departments:{type:Array,default:()=>[]},canProtect:Boolean})
</script>
