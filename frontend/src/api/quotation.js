import request from './request'
export const pageQuotations=params=>request.get('/quotations',{params})
export const getQuotation=id=>request.get('/quotations/'+id)
export const saveQuotation=data=>data.id?request.put('/quotations/'+data.id,data):request.post('/quotations',data)
export const reviseQuotation=(id,reason)=>request.post('/quotations/'+id+'/revision',{reason})
export const transitionQuotation=(id,action,note)=>request.post('/quotations/'+id+'/transition',{action,note})
export const quotationCustomers=keyword=>request.get('/quotations/customers',{params:{keyword}})
export const quotationOpportunities=params=>request.get('/quotations/opportunities',{params})
export const quotationContacts=params=>request.get('/quotations/contacts',{params})
export const quotationSkus=keyword=>request.get('/quotations/skus',{params:{keyword}})

export const deleteQuotation=id=>request.delete('/quotations/'+id)
