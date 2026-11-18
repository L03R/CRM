import request from './request'
export const getOverview=()=>request.get('/dashboard/overview')
export const getTasks=params=>request.get('/dashboard/tasks',{params})
export const getAgenda=params=>request.get('/dashboard/agenda',{params})
export const getReport=(kind,params)=>request.get('/dashboard/reports/'+kind,{params})
export const getSchedules=params=>request.get('/dashboard/schedules',{params})
export const saveSchedule=form=>form.id?request.put('/dashboard/schedules/'+form.id,form):request.post('/dashboard/schedules',form)
