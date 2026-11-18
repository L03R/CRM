package com.crm.service;

import com.crm.entity.Permission;
import com.crm.mapper.SystemQueryMapper;
import com.crm.vo.EmployeeVO;
import com.crm.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import com.crm.common.PageResult;
import com.crm.dto.PageQuery;

@Service
@RequiredArgsConstructor
public class SystemQueryService {
    private final SystemQueryMapper mapper;

    public PageResult<EmployeeVO> employeePage(String keyword,Long deptId,Integer status,PageQuery page) {
        List<EmployeeVO> records=mapper.employeePage(keyword,deptId,status,page.getOffset(),page.getSize());
        records.forEach(user->user.setRoleIds(mapper.findUserRoleIds(user.getId())));
        return new PageResult<>(records,mapper.employeeCount(keyword,deptId,status));
    }

    public List<EmployeeVO> employees(String keyword, Long deptId, Integer status) {
        List<EmployeeVO> employees = mapper.findEmployees(keyword, deptId, status);
        employees.forEach(user -> user.setRoleIds(mapper.findUserRoleIds(user.getId())));
        return employees;
    }

    public List<RoleVO> roles(String keyword) {
        List<RoleVO> roles = mapper.findRoles(keyword);
        roles.forEach(role -> role.setPermissionIds(mapper.findRolePermissionIds(role.getId())));
        return roles;
    }

    public List<Permission> permissions() {
        return mapper.findPermissions();
    }
}
