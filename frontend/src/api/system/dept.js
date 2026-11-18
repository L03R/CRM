import request from '@/api/request'

export function listDeptTree() {
  return request.get('/depts')
}

export function saveDept(data) {
  return data.id
    ? request.put(`/depts/${data.id}`, data)
    : request.post('/depts', data)
}

export function removeDept(id) {
  return request.delete(`/depts/${id}`)
}
