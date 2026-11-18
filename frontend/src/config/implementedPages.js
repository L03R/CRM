// 仅标记业务已接入真实接口的页面，不代表员工自动拥有这些页面的权限。
export const implementedPages = new Set([
  'delivery/after-sale','delivery/progress','delivery/shipment',
  'order/index',
  'sales/quotation',
  'customer/contact',
  'workbench/todo','workbench/schedule',
  'analysis/lead','analysis/customer','analysis/business',
  'workbench/index','product/index','product/category',
  'clue/index','customer/index','customer/pool','customer/follow','business/index',
  'system/employee','system/dept','system/role','system/permission','system/log'
])
