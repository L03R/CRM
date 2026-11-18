<template>
  <div class="login-page">
    <!-- 左侧品牌区 -->
    <section class="brand">
      <div class="brand-inner">
        <div class="brand-logo">
          <el-icon :size="22"><Grid /></el-icon>
        </div>
        <h1>{{ companyName }}</h1>
        <p class="brand-desc">
          吉他与乐器配件 · 工贸一体客户关系管理系统。
        </p>

        <ul class="brand-points">
          <li><el-icon><Aim /></el-icon>线索统一归集，经理分配跟进</li>
          <li><el-icon><TrendCharts /></el-icon>客户持续维护，商机分阶段推进</li>
          <li><el-icon><Tickets /></el-icon>自制、委外、外购产品统一建档</li>
        </ul>

      </div>
      <div class="glow glow-1"></div>
      <div class="glow glow-2"></div>
    </section>

    <!-- 右侧登录表单 -->
    <section class="panel">
      <div class="form-wrap">
        <h2 class="form-title">账号登录</h2>
        <p class="form-sub">请使用员工编号与登录密码进入系统</p>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @keyup.enter="handleLogin"
        >
          <el-form-item label="员工编号" prop="employeeNo">
            <el-input
              v-model.trim="form.employeeNo"
              placeholder="请输入员工编号，如 ADMIN001"
              :prefix-icon="User"
              clearable
              autocomplete="username"
            />
          </el-form-item>

          <el-form-item label="登录密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入登录密码"
              :prefix-icon="Lock"
              show-password
              autocomplete="current-password"
            />
          </el-form-item>

          <div class="form-row">
            <el-checkbox v-model="remember">记住员工编号</el-checkbox>
            <el-link type="primary" underline="never" @click="onForgot">忘记密码？</el-link>
          </div>

          <el-button
            class="submit"
            type="primary"
            :loading="loading"
            @click="handleLogin"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form>

        <DevQuickLogin v-if="DevQuickLogin" :disabled="loading" @select="quickLogin" />

        <div class="demo-tip">
          <el-icon><InfoFilled /></el-icon>
          <span>使用真实员工账号登录，首次使用请联系管理员获取账号。</span>
        </div>

        <p class="copyright">© {{ companyName }} · 企业内部系统</p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { defineAsyncComponent, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { companyName } from '@/config/company'

const REMEMBER_KEY = 'crm_remember_employee_no'
const DevQuickLogin = import.meta.env.DEV
  ? defineAsyncComponent(() => import('./DevQuickLogin.vue'))
  : null

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const remember = ref(false)

const form = reactive({
  employeeNo: '',
  password: ''
})

const rules = {
  employeeNo: [
    { required: true, message: '请输入员工编号', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_-]{3,32}$/,
      message: '员工编号为 3-32 位字母、数字、下划线或短横线',
      trigger: 'blur'
    }
  ],
  password: [
    { required: true, message: '请输入登录密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6-32 位', trigger: 'blur' }
  ]
}

onMounted(() => {
  const saved = localStorage.getItem(REMEMBER_KEY)
  if (saved) {
    form.employeeNo = saved
    remember.value = true
  }
})

function onForgot() {
  ElMessage.info('请联系系统管理员重置密码')
}

async function quickLogin(account) {
  if (loading.value) return
  form.employeeNo = account.employeeNo
  form.password = account.password
  await handleLogin()
}

async function handleLogin() {
  if (loading.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    await userStore.login({ employeeNo: form.employeeNo, password: form.password })

    if (remember.value) {
      localStorage.setItem(REMEMBER_KEY, form.employeeNo)
    } else {
      localStorage.removeItem(REMEMBER_KEY)
    }

    ElMessage.success(`欢迎回来，${userStore.displayName}`)
    const redirect = typeof route.query.redirect === 'string' && userStore.canVisit(route.query.redirect)
      ? route.query.redirect : '/'
    router.replace(redirect)
  } catch (err) {
    ElMessage.error(err?.response?.data?.message || err?.message || '登录失败，请检查员工编号和密码')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  height: 100%;
  background: #fff;
}

/* 左侧 */
.brand {
  position: relative;
  width: 46%;
  max-width: 620px;
  flex-shrink: 0;
  padding: 56px 56px 0;
  color: #fff;
  overflow: hidden;
  background: linear-gradient(155deg, #0f1b3d 0%, #1b2f6b 45%, #2f4bd0 100%);
  display: flex;
  align-items: center;
}

.brand-inner {
  position: relative;
  z-index: 2;
  padding-bottom: 56px;
}

.brand-logo {
  width: 46px;
  height: 46px;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(6px);
  margin-bottom: 28px;
}

.brand h1 {
  font-size: 28px;
  line-height: 1.35;
  margin: 0 0 14px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.brand-desc {
  margin: 0 0 34px;
  font-size: 14px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.72);
  max-width: 380px;
}

.brand-points {
  list-style: none;
  padding: 0;
  margin: 0 0 44px;
}

.brand-points li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
  margin-bottom: 16px;
}

.brand-points .el-icon {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.12);
  color: #bcd0ff;
}

.brand-stats {
  display: flex;
  gap: 36px;
  padding-top: 26px;
  border-top: 1px solid rgba(255, 255, 255, 0.14);
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.stat-num {
  font-size: 22px;
  font-weight: 600;
}

.stat-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
}

.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  opacity: 0.55;
}

.glow-1 {
  width: 340px;
  height: 340px;
  background: #3f6bff;
  right: -120px;
  top: -80px;
}

.glow-2 {
  width: 300px;
  height: 300px;
  background: #6a4bff;
  left: -110px;
  bottom: -140px;
  opacity: 0.4;
}

/* 右侧 */
.panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  overflow-y: auto;
}

.form-wrap {
  width: 100%;
  max-width: 380px;
  margin: auto 0;
}

.form-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.form-sub {
  margin: 8px 0 30px;
  font-size: 13px;
  color: var(--crm-text-sub);
}

.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -4px 0 22px;
}

.submit {
  width: 100%;
  height: 44px;
  font-size: 15px;
  letter-spacing: 2px;
  border-radius: 8px;
  box-shadow: 0 8px 18px rgba(47, 107, 255, 0.24);
}

.demo-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 24px;
  padding: 12px 14px;
  font-size: 12px;
  line-height: 1.6;
  color: #5a6572;
  background: #f6f8fc;
  border: 1px dashed #dbe3f0;
  border-radius: 8px;
}

.demo-tip .el-icon {
  color: var(--crm-brand);
}

.copyright {
  margin-top: 34px;
  font-size: 12px;
  color: #a8b2c1;
  text-align: center;
}

@media (max-width: 900px) {
  .brand {
    display: none;
  }
}
</style>
