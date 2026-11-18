import {findMenuPath} from './menu'
export function workItemLocation(menus,row) {
  const component=row.kind==='AFTERSALE'?'delivery/after-sale':row.kind==='LEAD'?'clue/index':'customer/index'
  const path=findMenuPath(menus,component)
  return path?{path,query:row.kind==='AFTERSALE'?{caseId:row.id}:{keyword:row.title}}:null
}
