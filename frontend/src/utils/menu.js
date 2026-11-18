export function findMenuPath(menus, component) {
  for (const menu of menus) {
    if (menu.permissionType === 2 && menu.component === component) return menu.routePath
    const path = findMenuPath(menu.children || [], component)
    if (path) return path
  }
}
