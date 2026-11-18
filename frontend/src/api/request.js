import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, removeToken, removeStoredUser, removeStoredPermissions } from '@/utils/auth'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 15000
})

// 请求拦截：统一携带 token
request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截：按 { code, message, data } 约定解包
request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0 || body.code === 200) {
        return body.data
      }
      if (body.code === 401) {
        removeToken()
        removeStoredUser()
        removeStoredPermissions()
        window.location.href = '/login'
        return Promise.reject(new Error(body.message || '登录状态已失效'))
      }
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error) => {
    if (error.response?.status === 401 && error.config?.url !== '/auth/login') {
      removeToken()
      removeStoredUser()
      removeStoredPermissions()
      window.location.href = '/login'
    } else if (error.config?.url !== '/auth/login') {
      ElMessage.error(error.response?.data?.message || error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default request
