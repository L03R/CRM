<template>
  <div class="forbidden">
    <h2>没有访问权限</h2>
    <p>请联系管理员分配所需角色或权限。</p>
    <el-button type="primary" @click="back">返回可用页面</el-button>
    <el-button @click="logout">退出登录</el-button>
    <el-button v-if="store.isLogin" @click="passwordDialog.open()">修改密码</el-button>
    <ChangePassword ref="passwordDialog" />
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ref } from 'vue'
import ChangePassword from '@/components/ChangePassword.vue'
const passwordDialog=ref()
const router = useRouter()
const store = useUserStore()
const back = () => router.push(store.firstMenuPath())
async function logout() {
  await store.logout()
  router.replace('/login')
}
</script>

<style scoped>
.forbidden { padding: 80px 24px; text-align: center; }
</style>
