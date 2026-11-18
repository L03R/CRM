import request from '@/api/request'

export function listRoles(keyword = '') {
  return request.get('/system/roles', { params: { keyword } })
}

export function listPermissions() {
  return request.get('/system/permissions')
}

export function saveRole(data) {
  return data.id ? request.put(`/system/roles/${data.id}`, data) : request.post('/system/roles', data)
}

export function removeRole(id) {
  return request.delete(`/system/roles/${id}`)
}

export function assignPermissions(id, permissionIds) {
  return request.post(`/system/roles/${id}/permissions`, { permissionIds })
}
