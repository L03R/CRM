export const customerTypes={DEALER:'经销商',STORE:'琴行',BRAND:'品牌商/OEM',TRADER:'贸易公司',TRAINING:'培训机构',OTHER:'其他'}
export const leadStatuses={PENDING:'待分配',CONTACTING:'跟进中',CONVERTED:'已转客户',INVALID:'无效',CLOSED:'已关闭'}
export const relationships={POTENTIAL:'潜在客户',COOPERATING:'合作中',PAUSED:'暂停合作'}
export const followMethods={PHONE:'电话',WECHAT:'微信',EMAIL:'邮件',VISIT:'拜访',EXHIBITION:'展会',OTHER:'其他'}
export const opportunityStages={INITIAL:'初步接洽',QUALIFIED:'需求确认',PROPOSAL:'方案/报价沟通',NEGOTIATION:'商务协商',WON:'已赢单',LOST:'已丢单'}
export const ownershipActions={CREATE:'创建',ASSIGN:'分配/转交',RETURN:'退回待分配',CONVERT:'转为客户',CLOSED:'关闭',INVALID:'作废',POOL_REQUEST:'申请退回公海',POOL_APPROVE:'审核通过进入公海',POOL_REJECT:'驳回公海申请',CLAIM:'领取公海客户'}
export const timeText=value=>value?String(value).replace('T',' '):'—'
export const hasAction=(row,action)=>(row.actions||[]).includes(action)
export function localTime(date=new Date()){return new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,19)}
export const prospectRules={
  companyName:[{required:true,message:'请输入公司/客户名称'}],
  customerType:[{required:true,message:'请选择客户类型'}],
  region:[{required:true,message:'请输入地区'}],
  source:[{required:true,message:'请输入来源'}],
  contactName:[{required:true,message:'请输入主要联系人'}],
  email:[{type:'email',message:'邮箱格式不正确'}],
  demand:[{required:true,message:'请填写初步需求'}],
  targetDeptId:[{required:true,message:'请选择意向销售部门'}]
}
export const emptyProspect=()=>({companyName:'',customerType:'STORE',region:'',source:'',contactName:'',phone:'',email:'',demand:'',targetDeptId:null,nextFollowAt:null,address:'',remark:'',relationship:'POTENTIAL',protectedCustomer:false})
