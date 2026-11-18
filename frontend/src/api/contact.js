import request from './request'
export const pageContacts=params=>request.get('/contacts',{params})
export const contactCustomers=keyword=>request.get('/contacts/customers',{params:{keyword}})
export const saveContact=form=>form.id?request.put('/contacts/'+form.id,form):request.post('/contacts',form)
export const setPrimaryContact=id=>request.post('/contacts/'+id+'/primary')
export const setContactStatus=(id,status)=>request.put('/contacts/'+id+'/status',null,{params:{status}})
