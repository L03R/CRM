package com.crm.security;

import com.crm.entity.User;
import com.crm.mapper.DeptMapper;
import com.crm.mapper.WorkflowMapper;
import com.crm.service.PermissionService;
import com.crm.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@RequiredArgsConstructor
public class WorkflowAccess {
    private final WorkflowMapper mapper;
    private final DeptMapper departments;
    private final PermissionService permissions;

    // 每个操作只合并明确授予该操作的角色范围，不借用其他角色的“全部”。
    public Map<String,DataScope> scopes(User user) {
        Set<String> enabled=new HashSet<>(permissions.codes(permissions.permissions(user.getId())));
        List<DeptVO> tree=departments.findAll();
        Map<String,List<Integer>> grants=new HashMap<>();
        for(WorkflowGrantVO grant:mapper.grants(user.getId())) if(enabled.contains(grant.permissionCode()))
            grants.computeIfAbsent(grant.permissionCode(),key->new ArrayList<>()).add(grant.dataScope());
        Map<String,DataScope> result=new HashMap<>();
        grants.forEach((code,ranges)->{
            Set<Long> ids=new LinkedHashSet<>();
            if(ranges.contains(2)) {
                ids.add(user.getDeptId());
                boolean changed;
                do {changed=false;for(DeptVO dept:tree) if(ids.contains(dept.getParentId())) changed|=ids.add(dept.getId());} while(changed);
            }
            if(ranges.contains(3)) ids.add(user.getDeptId());
            result.put(code,new DataScope(user.getId(),user.getDeptId(),ranges.contains(1),List.copyOf(ids)));
        });
        return result;
    }

    public DataScope require(Map<String,DataScope> scopes,String code) {
        DataScope scope=scopes.get(code);
        if(scope==null) throw new AccessDeniedException("没有操作权限");
        return scope;
    }
    public boolean lead(DataScope scope,LeadVO lead,boolean creatorRead) {
        if(scope==null) return false;
        boolean managed=lead.getOwnerId()==null ? scope.all() || scope.deptIds().contains(lead.getTargetDeptId()) : scope.owns(lead.getOwnerId(),lead.getOwnerDeptId());
        return managed || creatorRead && (scope.userId().equals(lead.getCreatedBy()) || scope.deptIds().contains(lead.getCreatorDeptId()));
    }
    public boolean customer(DataScope scope,CustomerVO customer) {
        return scope!=null && customer.getOwnerId()!=null && scope.owns(customer.getOwnerId(),customer.getOwnerDeptId());
    }
    public void check(boolean allowed) {if(!allowed)throw new AccessDeniedException("无权访问或操作这条业务数据");}
}
