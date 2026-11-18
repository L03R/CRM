<template>
  <!-- 目录：递归渲染子级 -->
  <el-sub-menu v-if="node.children && node.children.length" :index="`dir-${node.id}`">
    <template #title>
      <el-icon><component :is="node.icon || 'Menu'" /></el-icon>
      <span>{{ node.permissionName }}</span>
    </template>
    <MenuNode v-for="child in node.children" :key="child.id" :node="child" />
  </el-sub-menu>

  <!-- 菜单：routePath 对应前端路由 -->
  <el-menu-item v-else-if="node.routePath" :index="node.routePath">
    <el-icon><component :is="node.icon || 'Menu'" /></el-icon>
    <span>{{ node.permissionName }}</span>
  </el-menu-item>
</template>

<script setup>
defineProps({
  node: { type: Object, required: true }
})
</script>
