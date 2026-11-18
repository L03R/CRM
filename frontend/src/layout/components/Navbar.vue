<template>
  <header class="navbar">
    <el-icon class="collapse-btn" :size="18" @click="$emit('toggle')">
      <component :is="collapse ? 'Expand' : 'Fold'" />
    </el-icon>

    <el-breadcrumb separator="/" class="crumb">
      <el-breadcrumb-item :to="{ path: '/' }">工作台</el-breadcrumb-item>
      <el-breadcrumb-item v-for="item in trail" :key="item.id">
        {{ item.permissionName }}
      </el-breadcrumb-item>
    </el-breadcrumb>

    <div class="right">
      <el-tag v-if="userStore.userInfo?.deptName" type="info" effect="plain" round>
        {{ userStore.userInfo.deptName }}
      </el-tag>

      <el-dropdown trigger="click" @command="handleCommand">
        <div class="user">
          <div class="avatar">{{ userStore.avatarText }}</div>
          <div class="user-meta">
            <span class="user-name">{{ userStore.displayName }}</span>
            <span class="user-role">{{ userStore.roleNames || '员工' }}</span>
          </div>
          <el-icon :size="12" class="arrow"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled>
              员工编号：{{ userStore.userInfo?.employeeNo || '-' }}
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>退出登录
            </el-dropdown-item>
            <el-dropdown-item command="password">修改密码</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
  <ChangePassword ref="passwordDialog" />
</template>

<script setup>
import { computed, ref } from 'vue'
import ChangePassword from '@/components/ChangePassword.vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'

defineProps({
  collapse: { type: Boolean, default: false }
})
defineEmits(['toggle'])

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const passwordDialog=ref()

/** 在权限菜单树中查找当前路由的层级链（用于面包屑） */
const trail = computed(() => {
  const result = []
  const walk = (nodes, path) => {
    for (const node of nodes) {
      if (node.routePath === route.path) {
        result.push(...path, node)
        return true
      }
      if (node.children && walk(node.children, [...path, node])) return true
    }
    return false
  }
  walk(userStore.menuTree, [])
  return result.filter(item => item.permissionCode !== 'menu:workbench')
})

async function handleCommand(command) {
  if (command === 'password') { passwordDialog.value.open(); return }
  if (command !== 'logout') return
  try {
    await ElMessageBox.confirm('确定要退出当前账号吗？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  await userStore.logout()
  ElMessage.success('已退出登录')
  router.replace('/login')
}
</script>

<style scoped>
.navbar {
  height: 60px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid var(--crm-border);
}

.collapse-btn {
  cursor: pointer;
  color: #5a6572;
  padding: 6px;
  border-radius: 6px;
  transition: background 0.15s;
}

.collapse-btn:hover {
  background: #f2f4f8;
  color: var(--crm-brand);
}

.crumb :deep(.el-breadcrumb__inner) {
  font-weight: 400;
}

.right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 16px;
}

.user {
  display: flex;
  align-items: center;
  gap: 9px;
  cursor: pointer;
  padding: 4px 8px 4px 4px;
  border-radius: 8px;
  outline: none;
}

.user:hover {
  background: #f5f7fb;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #4f8bff, #7b5cff);
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
}

.user-role {
  font-size: 11px;
  color: var(--crm-text-sub);
}

.arrow {
  color: #98a2b3;
}
</style>
