package com.crm.mapper;

import com.crm.entity.Permission;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface PermissionMapper {
    List<Permission> findAll();
    Permission findById(Long id);
    void insert(Permission permission);
    void update(Permission permission);
    void updateStatus(@Param("id") Long id, @Param("status") Integer status);
    int countChildren(Long id);
    int countBusinessGrants(Long id);
    void grantAdmin(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
    void deleteAdminGrant(Long id);
    void delete(Long id);
}
