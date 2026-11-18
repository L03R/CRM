package com.crm.mapper;

import com.crm.entity.Permission;
import com.crm.vo.EmployeeVO;
import com.crm.vo.RoleVO;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface SystemQueryMapper {
    List<EmployeeVO> employeePage(@Param("keyword") String keyword,@Param("deptId") Long deptId,@Param("status") Integer status,@Param("offset") int offset,@Param("size") int size);
    long employeeCount(@Param("keyword") String keyword,@Param("deptId") Long deptId,@Param("status") Integer status);
    List<EmployeeVO> findEmployees(@Param("keyword") String keyword, @Param("deptId") Long deptId,
                                   @Param("status") Integer status);
    List<Long> findUserRoleIds(Long userId);
    List<RoleVO> findRoles(String keyword);
    List<Long> findRolePermissionIds(Long roleId);
    List<Permission> findPermissions();
}
