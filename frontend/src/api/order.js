import request from './request'
export const generateOrder=id=>request.post('/orders/from-quotation/'+id)
export const pageOrders=params=>request.get('/orders',{params})
export const getOrder=id=>request.get('/orders/'+id)
export const updateOrder=(id,data)=>request.put('/orders/'+id,data)
export const confirmOrder=id=>request.post('/orders/'+id+'/confirm')
export const cancelOrder=(id,reason)=>request.post('/orders/'+id+'/cancel',{reason})
export const orderSkus=(id,keyword)=>request.get('/orders/'+id+'/skus',{params:{keyword}})
export const changeOrder=(id,data)=>request.post('/orders/'+id+'/change',data)
export const cancelConfirmedOrder=(id,data)=>request.post('/orders/'+id+'/cancel-confirmed',data)

export const deleteOrder=id=>request.delete('/orders/'+id)
