<template>
  <aside class="sidebar" :class="{ 'is-collapse': collapse }">
    <div class="logo">
      <div class="logo-mark">
        <el-icon :size="18"><Grid /></el-icon>
      </div>
      <transition name="fade">
        <div v-show="!collapse" class="logo-text">
          <span class="logo-name">{{ brandName }}</span>
          <span class="logo-sub">工贸一体 · CRM</span>
        </div>
      </transition>
    </div>

    <el-scrollbar class="menu-scroll">
      <el-menu
        class="sidebar-menu"
        :default-active="route.path"
        :collapse="collapse"
        :collapse-transition="false"
        router
      >
        <MenuNode v-for="menu in userStore.menuTree" :key="menu.id" :node="menu" />
      </el-menu>
    </el-scrollbar>

    <div v-show="!collapse" class="sidebar-footer">
      <span class="dot"></span>
      <span>{{ userStore.roleNames || '未分配角色' }} · v0.2.0</span>
    </div>
  </aside>
</template>

<script setup>
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import MenuNode from './MenuNode.vue'
import { brandName } from '@/config/company'

defineProps({ collapse: Boolean })

const route = useRoute()
const userStore = useUserStore()
</script>

<style scoped>
.sidebar {
  width: 228px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-right: 1px solid var(--crm-border);
  transition: width 0.22s ease;
  overflow: hidden;
}

.sidebar.is-collapse {
  width: 64px;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid var(--crm-border);
  overflow: hidden;
}

.logo-mark {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, #2f6bff 0%, #6a4bff 100%);
  box-shadow: 0 4px 10px rgba(47, 107, 255, 0.28);
}

.logo-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  white-space: nowrap;
}

.logo-name {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.4px;
}

.logo-sub {
  font-size: 11px;
  color: var(--crm-text-sub);
  margin-top: 2px;
}

.menu-scroll {
  flex: 1;
  padding: 8px 0;
}

.sidebar-menu {
  border-right: none;
  background: transparent;
}

.sidebar-menu :deep(.el-menu-item),
.sidebar-menu :deep(.el-sub-menu__title) {
  height: 42px;
  line-height: 42px;
  margin: 2px 10px;
  border-radius: 8px;
  font-size: 14px;
  color: #4a5568;
}

.sidebar-menu :deep(.el-sub-menu__title:hover),
.sidebar-menu :deep(.el-menu-item:hover) {
  background: #f5f7fb;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: var(--crm-brand-light);
  color: var(--crm-brand);
  font-weight: 600;
}

.sidebar-menu :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: var(--crm-brand);
  font-weight: 600;
}

.sidebar-menu :deep(.el-menu--inline) {
  background: transparent;
}

.sidebar-menu :deep(.el-menu--inline .el-menu-item) {
  padding-left: 42px !important;
  font-size: 13px;
  height: 38px;
  line-height: 38px;
}

.sidebar.is-collapse .sidebar-menu :deep(.el-menu-item),
.sidebar.is-collapse .sidebar-menu :deep(.el-sub-menu__title) {
  margin: 2px 8px;
}

.sidebar-footer {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 12px 18px;
  font-size: 12px;
  color: #98a2b3;
  border-top: 1px solid var(--crm-border);
  white-space: nowrap;
  overflow: hidden;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22c55e;
  flex-shrink: 0;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
