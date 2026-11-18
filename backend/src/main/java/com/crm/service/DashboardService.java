package com.crm.service;

import com.crm.common.BusinessException;
import com.crm.dto.PageQuery;
import com.crm.common.PageResult;
import com.crm.dto.ScheduleRequest;
import com.crm.entity.*;
import com.crm.mapper.DashboardMapper;
import com.crm.security.*;
import com.crm.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final DashboardMapper mapper;
    private final WorkflowAccess access;
    private final PermissionService permissions;
    private static final Map<String,String> READ_CODES=Map.of("lead","clue:list","customer","customer:list","business","business:list");

    public ReportVO report(User user,String kind,LocalDate start,LocalDate end) {
        if(!READ_CODES.containsKey(kind)) throw new BusinessException("分析类型不正确");
        dateRange(start,end);
        var scopes=access.scopes(user);
        var report=access.require(scopes,"report:"+kind+":list");
        var read=access.require(scopes,READ_CODES.get(kind));
        return new ReportVO(mapper.groups(kind,"state",report,read,start,end),
                mapper.groups(kind,"trend",report,read,start,end),
                kind.equals("lead")?mapper.groups(kind,"source",report,read,start,end):List.of(),
                kind.equals("customer")?Map.of("todayFollow",mapper.customerDue(intersect(report,read),false),"overdueFollow",mapper.customerDue(intersect(report,read),true)):Map.of());
    }

    public WorkbenchVO overview(User user) {
        checkPage(user,"workbench/index");
        var scopes=access.scopes(user);
        Map<String,Long> metrics=new LinkedHashMap<>();
        List<WorkItemVO> todos=new ArrayList<>();
        for(var entry:READ_CODES.entrySet()) {
            var scope=scopes.get(entry.getValue());
            if(scope==null) continue;
            var points=mapper.groups(entry.getKey(),"state",scope,scope,null,null);
            if(entry.getKey().equals("lead")) {
                todos.addAll(mapper.todos("leadFollow",scope));
                metrics.put("leadContacting",points.stream().filter(p->p.name().equals("CONTACTING")).mapToLong(ChartPointVO::value).sum());
                metrics.put("leadConverted",points.stream().filter(p->p.name().equals("CONVERTED")).mapToLong(ChartPointVO::value).sum());
            }
            metrics.put(entry.getKey()+"Total",points.stream().mapToLong(ChartPointVO::value).sum());
            if(entry.getKey().equals("business")) metrics.put("activeBusiness",points.stream()
                    .filter(p->!Set.of("WON","LOST").contains(p.name())).mapToLong(ChartPointVO::value).sum());
            if(entry.getKey().equals("customer")) {
                metrics.put("todayFollow",mapper.customerDue(scope,false));
                metrics.put("overdueFollow",mapper.customerDue(scope,true));
                todos.addAll(mapper.todos("follow",scope));
                var currentMonth=LocalDate.now().withDayOfMonth(1);
                metrics.put("monthCustomers",mapper.groups("customer","state",scope,scope,currentMonth,LocalDate.now())
                        .stream().mapToLong(ChartPointVO::value).sum());
            }
        }
        addActionTodos(scopes,"clue:assign","clue:list","assign",todos,metrics);
        addActionTodos(scopes,"customer:pool:approve","customer:list","review",todos,metrics);
        var afterScopes=taskScopes(user);
        if(afterScopes.containsKey("aftersale")) {
            var afterTodos=mapper.tasks(afterScopes,"AFTERSALE_FOLLOW","ALL",null,null,null,0,5);
            todos.addAll(afterTodos.stream().filter(item->!item.dueAt().isAfter(LocalDate.now().atTime(23,59,59))).toList());
            metrics.put("aftersalePending",mapper.taskCount(afterScopes,"AFTERSALE_FOLLOW","ALL",null,null,null));
        }
        todos.sort(Comparator.comparing(WorkItemVO::dueAt));
        return new WorkbenchVO(metrics,todos.stream().limit(5).toList());
    }

    public PageResult<WorkItemVO> tasks(User user,String type,String due,String keyword,PageQuery page) {
        checkPage(user,"workbench/todo");
        if(!Set.of("ALL","LEAD_FOLLOW","CUSTOMER_FOLLOW","AFTERSALE_FOLLOW","ASSIGN","REVIEW").contains(type) || !Set.of("ALL","TODAY","OVERDUE").contains(due))
            throw new BusinessException("待办筛选条件不正确");
        var scopes=taskScopes(user);
        return new PageResult<>(mapper.tasks(scopes,type,due,keyword,null,null,page.getOffset(),page.getSize()),
                mapper.taskCount(scopes,type,due,keyword,null,null));
    }

    public List<WorkItemVO> agenda(User user,LocalDate start,LocalDate end) {
        checkPage(user,"workbench/schedule");dateRange(start,end);
        var scopes=taskScopes(user);
        // Calendar pages request one displayed month, including adjacent weeks.
        return mapper.tasks(scopes,"ALL","ALL",null,start,end,0,0);
    }

    private Map<String,DataScope> taskScopes(User user) {
        var grants=access.scopes(user);
        Map<String,DataScope> result=new HashMap<>();
        if(grants.containsKey("clue:list")) result.put("lead",grants.get("clue:list"));
        if(grants.containsKey("customer:list")) result.put("customer",grants.get("customer:list"));
        if(grants.containsKey("clue:assign") && grants.containsKey("clue:list")) result.put("assign",intersect(grants.get("clue:assign"),grants.get("clue:list")));
        if(grants.containsKey("customer:pool:approve") && grants.containsKey("customer:list")) result.put("review",intersect(grants.get("customer:pool:approve"),grants.get("customer:list")));
        if(grants.containsKey("aftersale:list") && grants.containsKey("aftersale:follow")) result.put("aftersale",intersect(grants.get("aftersale:list"),grants.get("aftersale:follow")));
        return result;
    }

    private void addActionTodos(Map<String,DataScope> scopes,String operation,String read,String kind,
                                List<WorkItemVO> todos,Map<String,Long> metrics) {
        if(!scopes.containsKey(operation) || !scopes.containsKey(read)) return;
        DataScope scope=intersect(scopes.get(operation),scopes.get(read));
        todos.addAll(mapper.todos(kind,scope));
        metrics.put(kind+"Pending",mapper.todoCount(kind,scope));
    }

    private DataScope intersect(DataScope first,DataScope second) {
        Set<Long> ids=new LinkedHashSet<>(first.all()?second.deptIds():first.deptIds());
        if(!second.all()) ids.retainAll(second.deptIds());
        return new DataScope(first.userId(),first.userDeptId(),first.all() && second.all(),List.copyOf(ids));
    }

    public List<Schedule> schedules(User user,LocalDate start,LocalDate end) {
        checkPage(user,"workbench/schedule");dateRange(start,end);
        return mapper.schedules(user.getId(),start,end);
    }

    @Transactional
    public Long saveSchedule(User user,Long id,ScheduleRequest request) {
        checkPage(user,"workbench/schedule");
        if(request.endAt().isBefore(request.startAt())) throw new BusinessException("结束时间不能早于开始时间");
        if(request.remindAt()!=null && request.remindAt().isAfter(request.startAt())) throw new BusinessException("提醒时间不能晚于开始时间");
        if(id!=null && mapper.schedule(id,user.getId())==null) throw new AccessDeniedException("只能维护自己的日程");
        Schedule schedule=new Schedule();
        schedule.setId(id);schedule.setUserId(user.getId());schedule.setTitle(request.title().trim());
        schedule.setDescription(request.description().trim());schedule.setStartAt(request.startAt());
        schedule.setEndAt(request.endAt());schedule.setRemindAt(request.remindAt());
        schedule.setPriority(request.priority());schedule.setStatus(request.status());
        if(id==null) mapper.insertSchedule(schedule); else mapper.updateSchedule(schedule);
        return schedule.getId();
    }

    private void checkPage(User user,String component) {
        boolean allowed=permissions.permissions(user.getId()).stream().anyMatch(p->component.equals(p.getComponent()) && p.getPermissionType()==2);
        if(!allowed) throw new AccessDeniedException("没有页面权限");
    }

    private void dateRange(LocalDate start,LocalDate end) {
        if(start==null || end==null || end.isBefore(start) || end.isAfter(start.plusYears(1)))
            throw new BusinessException("请选择一年以内的有效日期范围");
    }
}
