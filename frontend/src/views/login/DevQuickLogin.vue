<template>
  <div class="quick-login">
    <div class="quick-title">开发测试 · 一键登录</div>
    <div class="quick-grid">
      <el-button v-for="account in accounts" :key="account.employeeNo" :disabled="disabled" plain @click="$emit('select', account)">
        <span><strong>{{ account.label }}</strong><small>{{ account.employeeNo }}</small></span>
      </el-button>
    </div>
    <p>使用已初始化的真实测试账号；修改过密码的账号请手动登录。</p>
  </div>
</template>

<script setup>
defineProps({ disabled: Boolean })
defineEmits(['select'])

// Development-only component. Remove this file and its login-page entry when no longer needed.
const accounts = [
  { label: '系统管理员', employeeNo: 'ADMIN001', password: 'Admin@123456' },
  { label: '销售经理', employeeNo: 'MANAGER001', password: 'Manager@123456' },
  { label: '华东销售', employeeNo: 'SALES001', password: 'Sales@123456' },
  { label: '华南销售', employeeNo: 'SALES002', password: 'Sales2@123456' },
  { label: '市场运营', employeeNo: 'MARKET001', password: 'Market@123456' },
  { label: '客服人员', employeeNo: 'SERVICE001', password: 'Service@123456' },
  { label: '生产人员', employeeNo: 'PROD001', password: 'Produce@123456' },
  { label: '财务人员', employeeNo: 'FIN001', password: 'Finance@123456' },
  { label: '销售＋客服', employeeNo: 'MULTI001', password: 'Multi@123456' },
  { label: '采购仓储', employeeNo: 'SUPPLY001', password: 'Supply@123456' },
  { label: '产品资料', employeeNo: 'PRODUCT001', password: 'Product@123456' }
]
</script>

<style scoped>
.quick-login { margin-top: 20px; padding-top: 16px; border-top: 1px solid #e5e7eb; }
.quick-title { margin-bottom: 10px; font-size: 13px; color: #5a6572; }
.quick-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.quick-grid .el-button { width: 100%; height: 48px; margin: 0; padding: 6px; }
.quick-grid strong { display: block; font-size: 12px; font-weight: 500; }
.quick-grid small { display: block; margin-top: 4px; font-size: 10px; opacity: .65; }
.quick-login p { margin: 10px 0 0; color: #98a2b3; font-size: 11px; line-height: 1.5; }
</style>
