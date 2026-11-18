import request from './request'
export const pageAfterSales=params=>request.get('/after-sales',{params})
export const getAfterSale=id=>request.get('/after-sales/'+id)
export const afterSaleCustomers=keyword=>request.get('/after-sales/customers',{params:{keyword}})
export const afterSaleSources=params=>request.get('/after-sales/sources',{params})
export const afterSalePeople=id=>request.get('/after-sales/'+id+'/people')
export const createAfterSale=data=>request.post('/after-sales',data)
export const updateAfterSaleSource=(id,data)=>request.put('/after-sales/'+id+'/source',data)
export const afterSaleAction=(id,action,data)=>request.post('/after-sales/'+id+'/'+action,data)
export const uploadAfterSaleImage=(id,file)=>{const body=new FormData();body.append('file',file);return request.post('/after-sales/'+id+'/images',body)}
export const getAfterSaleImage=(caseId,id)=>request.get('/after-sales/'+caseId+'/images/'+id,{responseType:'blob'})

export const afterSaleOwners=()=>request.get('/after-sales/owners')
