import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi, getProfile, getMenus } from '@/api/auth'
import {
  getToken,
  setToken,
  removeToken,
  getStoredUser,
  setStoredUser,
  removeStoredUser,
  getStoredPermissions,
  setStoredPermissions,
  removeStoredPermissions
} from '@/utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    userInfo: getStoredUser(),
    menus: [],
    // sys_permission 记录（后端登录/获取个人信息时返回）
    permissions: getStoredPermissions() || []
  }),

  getters: {
    isLogin: (state) => !!state.token,
    displayName: (state) => state.userInfo?.realName || '未登录',
    avatarText: (state) => (state.userInfo?.realName || 'U').slice(-2),
    roleNames: (state) =>
      (state.userInfo?.roles || []).map((r) => r.roleName).join('、'),

    menuTree: (state) => state.menus,

    /** 按钮级权限编码集合（type=3） */
    permCodes: (state) =>
      new Set(
        state.permissions
          .filter((p) => p.permissionType === 3 && p.status === 1)
          .map((p) => p.permissionCode)
      )
  },

  actions: {
    async loadProfile() {
      const [profile, menus] = await Promise.all([getProfile(), getMenus()])
      this.userInfo = profile.user
      this.permissions = profile.permissions
      this.menus = menus
      setStoredUser(profile.user)
      setStoredPermissions(profile.permissions)
    },

    canVisit(path) {
      return this.permissions.some((p) => p.permissionType === 2 && p.status === 1 && p.routePath === path)
    },

    firstMenuPath() {
      const firstPage = (nodes) => {
        for (const node of nodes) {
          if (node.permissionType === 2) return node.routePath
          const path = firstPage(node.children)
          if (path) return path
        }
        return null
      }
      return firstPage(this.menus) || '/403'
    },
    hasPerm(code) {
      return this.permCodes.has(code)
    },

    async login(payload) {
      const res = await loginApi(payload)
      this.token = res.token
      this.userInfo = res.user
      this.permissions = res.permissions || []
      setToken(res.token)
      setStoredUser(res.user)
      setStoredPermissions(res.permissions || [])
      return res
    },

    async logout() {
      try {
        await logoutApi()
      } finally {
        this.resetState()
      }
    },

    resetState() {
      this.token = ''
      this.userInfo = null
      this.permissions = []
      this.menus = []
      removeToken()
      removeStoredUser()
      removeStoredPermissions()
    }
  }
})
