export const quotationStatuses={DRAFT:'草稿',SENT:'已发送',ACCEPTED:'客户已接受',REJECTED:'客户未接受',VOID:'已作废',EXPIRED:'已过期'}
export const quotationEvents={DELETE:'删除报价',CREATE:'创建版本',EDIT:'修改草稿',REVISE:'修订版本',SEND:'确认已发送',ACCEPT:'客户接受',REJECT:'客户未接受',VOID:'作废报价',FINAL:'选定最终报价',UNFINAL:'替换最终报价',ORDER:'生成订单',CONFIRM_ORDER:'确认订单',CANCEL_ORDER:'取消订单草稿'}
export const quotationActions={delete:'删除',edit:'编辑草稿',send:'确认已发送',accept:'记录客户接受',reject:'记录客户未接受',revise:'修订新版本',final:'选为最终报价',void:'作废报价',order:'生成订单草稿',orderView:'查看关联订单'}
export const money=value=>Number(value || 0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
