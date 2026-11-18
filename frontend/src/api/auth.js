import request from './request'

/** 登录：员工编号 + 密码 → { token, user, permissions } */
export function login(data) {
  return request.post('/auth/login', data)
}

/** 退出登录 */
export function logout() {
  return request.post('/auth/logout')
}

/** 刷新页面后恢复登录态：获取当前登录人信息 + 权限 */
export function getProfile() {
  return request.get('/auth/profile')
}

export function getMenus() {
  return request.get('/auth/menus')
}
