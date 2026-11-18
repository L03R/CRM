import request from '@/api/request'

export const listPermissionTree = (params = {}) => request.get('/system/permissions/tree', { params })
export const listComponents = () => request.get('/system/permissions/components')
export const savePermission = (data) => data.id
  ? request.put(`/system/permissions/${data.id}`, data)
  : request.post('/system/permissions', data)
export const changePermissionStatus = (id, status) => request.put(`/system/permissions/${id}/status`, { status })
export const removePermission = (id) => request.delete(`/system/permissions/${id}`)
