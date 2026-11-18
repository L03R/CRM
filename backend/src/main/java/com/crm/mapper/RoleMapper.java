package com.crm.mapper;

import com.crm.entity.Role;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface RoleMapper {
    Role lockRole(Long id);
    void insert(Role role);
    void update(Role role);
    int countEmployees(Long roleId);
    void delete(Long id);
    void deletePermissions(Long roleId);
    void insertPermissions(@Param("roleId") Long roleId, @Param("ids") List<Long> ids);
}
