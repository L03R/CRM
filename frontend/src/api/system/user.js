import request from '@/api/request'

export function listUsers(query = {}) {
  return request.get('/system/users', { params: query })
}

export function createUser(data) {
  return request.post('/system/users', data)
}

export function assignUserRoles(id, roleIds) {
  return request.put(`/system/users/${id}/roles`, { roleIds })
}

export function listEmployeeDepartments() {
  return request.get('/system/users/departments')
}
export const pageUsers = (params) => request.get('/system/users/page', {params})
export const employeeOptions = () => request.get('/system/users/options')
export const updateUser = (id,data) => request.put(`/system/users/${id}`,data)
export const changeUserState = (id,data) => request.put(`/system/users/${id}/state`,data)
export const resetUserPassword = (id) => request.post(`/system/users/${id}/reset-password`)
