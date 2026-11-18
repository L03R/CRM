import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'
import Layout from '@/layout/index.vue'
import { useUserStore } from '@/store/user'
import { pageComponents } from './components'
import { companyName } from '@/config/company'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/login/index.vue'), meta: {title:'登录',public:true} },
    { path: '/403', component: () => import('@/views/error/forbidden.vue'), meta: {title:'无访问权限'} },
    { path: '/', name:'Layout', component:Layout, children:[] },
    { path: '/:pathMatch(.*)*', name:'NotFound', component:() => import('@/views/error/forbidden.vue') }
  ],
  scrollBehavior: () => ({top:0})
})

router.beforeEach(async (to) => {
  const store = useUserStore()
  if (!getToken()) return to.meta.public ? true : {path:'/login',query:{redirect:to.fullPath}}
  try { await store.loadProfile() }
  catch (error) {
    if (error.response?.status === 401) {store.resetState(); return '/login'}
    return false
  }

  const pages = store.permissions.filter(p => p.permissionType === 2 && pageComponents[p.component])
  const names = new Set(pages.map(p => 'permission-' + p.id))
  for (const route of router.getRoutes()) {
    if (String(route.name).startsWith('permission-') && !names.has(route.name)) router.removeRoute(route.name)
  }
  for (const page of pages) {
    const name = 'permission-' + page.id
    const existing = router.getRoutes().find(r => r.name === name)
    if (existing?.path === page.routePath && existing.meta.componentKey === page.component && existing.meta.title === page.permissionName) continue
    if (existing) router.removeRoute(name)
    router.addRoute('Layout',{path:page.routePath,name,component:pageComponents[page.component],
      meta:{title:page.permissionName,componentKey:page.component}})
  }

  if (to.path === '/' || to.path === '/login') return store.firstMenuPath()
  if (to.path === '/403') return true
  if (!store.canVisit(to.path)) return '/403'
  // 首次打开深层地址时，权限页面在本次导航中才注册，需要重新匹配。
  const resolved = router.resolve(to.fullPath)
  if (resolved.name === 'NotFound') return '/403'
  if (resolved.name !== to.name || resolved.meta.componentKey !== to.meta.componentKey || resolved.meta.title !== to.meta.title) return to.fullPath
  return true
})

router.afterEach(to => {
  document.title = to.meta.title ? `${to.meta.title} · ${companyName}` : `${companyName} · CRM`
})
export default router
