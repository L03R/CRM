const TOKEN_KEY = 'crm_token'
const USER_KEY = 'crm_user'
const PERM_KEY = 'crm_permissions'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export function getStoredUser() {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function setStoredUser(user) {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function removeStoredUser() {
  localStorage.removeItem(USER_KEY)
}

export function getStoredPermissions() {
  const raw = localStorage.getItem(PERM_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function setStoredPermissions(perms) {
  localStorage.setItem(PERM_KEY, JSON.stringify(perms))
}

export function removeStoredPermissions() {
  localStorage.removeItem(PERM_KEY)
}
