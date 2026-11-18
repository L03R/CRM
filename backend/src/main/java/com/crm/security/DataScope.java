package com.crm.security;

import java.util.List;
import java.util.Objects;

public record DataScope(Long userId,Long userDeptId,boolean all,List<Long> deptIds) {
    public boolean owns(Long ownerId,Long ownerDeptId) {
        return all || Objects.equals(userId,ownerId) || deptIds.contains(ownerDeptId);
    }
    public boolean pool(Long deptId) {return all || Objects.equals(userDeptId,deptId) || deptIds.contains(deptId);}
}
