package com.crm.service;

import com.crm.common.BusinessException;
import com.crm.dto.*;
import com.crm.mapper.DeptMapper;
import com.crm.entity.Role;
import com.crm.entity.User;
import com.crm.mapper.AuthMapper;
import com.crm.mapper.UserMapper;
import com.crm.security.TokenUtils;
import com.crm.vo.CreatedEmployeeVO;
import com.crm.vo.EmployeeOptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper mapper;
    private final AuthMapper authMapper;
    private final PasswordEncoder encoder;
    private final DeptMapper departments;

    public List<EmployeeOptionVO> options() {return mapper.options();}

    @Transactional
    public void update(Long id,UpdateEmployeeRequest request,Long operatorId) {
        mapper.lockAdminRole();
        departments.lockDepartments();
        User user=managedUser(id,operatorId);
        if (!user.getEmployeeNo().equals(request.employeeNo())) throw new BusinessException("员工编号不能修改");
        if (!user.getDeptId().equals(request.deptId()) && mapper.lockEnabledDepartment(request.deptId())==null)
            throw new BusinessException("请选择有效且启用的部门");
        user.setRealName(request.realName().trim()); user.setDeptId(request.deptId());
        user.setPhone(request.phone()); user.setEmail(request.email()); user.setHireDate(request.hireDate()); user.setRemark(request.remark());
        mapper.update(user);
    }

    @Transactional
    public void state(Long id,EmployeeStateRequest request,Long operatorId) {
        mapper.lockAdminRole();
        departments.lockDepartments();
        User user=managedUser(id,operatorId);
        if (request.employmentStatus()==0 && request.status()==1) throw new BusinessException("离职员工不能启用账号");
        if ((request.status()==0 || request.employmentStatus()==0) && isAdmin(id) && mapper.countOtherAdmins(id)==0)
            throw new BusinessException("至少保留一名有效的系统管理员");
        if (id.equals(operatorId) && request.status()==0) throw new BusinessException("不能停用自己的账号，请由其他管理员操作");
        user.setStatus(request.status()); user.setEmploymentStatus(request.employmentStatus());
        mapper.updateState(user);
        if (!user.canLogin()) {authMapper.revokeUserSessions(id); mapper.clearLeader(id);}
    }

    @Transactional
    public CreatedEmployeeVO resetPassword(Long id,Long operatorId) {
        mapper.lockAdminRole();
        User user=managedUser(id,operatorId);
        if (id.equals(operatorId)) throw new BusinessException("请使用右上角的修改密码功能修改自己的密码");
        String password="Crm@"+TokenUtils.create().substring(0,12);
        mapper.updatePassword(id,encoder.encode(password));
        authMapper.revokeUserSessions(id);
        return new CreatedEmployeeVO(id,user.getEmployeeNo(),password);
    }

    private User managedUser(Long id,Long operatorId) {
        User user=mapper.lockUser(id);
        if (user==null) throw new BusinessException("员工不存在");
        if (isAdmin(id) && !isAdmin(operatorId)) throw new AccessDeniedException("只有系统管理员可以维护管理员账号");
        return user;
    }

    @Transactional
    public CreatedEmployeeVO create(CreateEmployeeRequest request, Long operatorId) {
        // Serialize administrator role changes before validating any admin grants.
        mapper.lockAdminRole();
        List<Long> roleIds = validateRoles(request.roleIds(), operatorId);
        if (mapper.lockEnabledDepartment(request.deptId()) == null) {
            throw new BusinessException("请选择有效且启用的部门");
        }
        String password = "Crm@" + TokenUtils.create().substring(0, 12);
        User user = new User();
        user.setEmployeeNo(request.employeeNo());
        user.setRealName(request.realName().trim());
        user.setDeptId(request.deptId());
        user.setPhone(request.phone());
        user.setEmail(request.email());
        user.setHireDate(request.hireDate());
        user.setRemark(request.remark());
        user.setPasswordHash(encoder.encode(password));
        mapper.insert(user);
        if (!roleIds.isEmpty()) mapper.insertRoles(user.getId(), roleIds);
        return new CreatedEmployeeVO(user.getId(), user.getEmployeeNo(), password);
    }

    @Transactional
    public void assignRoles(Long userId, List<Long> ids, Long operatorId) {
        Long adminRoleId = mapper.lockAdminRole();
        User user = mapper.lockUser(userId);
        if (user == null) throw new BusinessException("员工不存在");
        List<Long> roleIds = validateRoles(ids, operatorId);
        boolean wasAdmin = isAdmin(userId);
        if (wasAdmin && !isAdmin(operatorId)) {
            throw new AccessDeniedException("只有系统管理员可以调整管理员的角色");
        }
        if (wasAdmin && user.canLogin() && !roleIds.contains(adminRoleId) && mapper.countOtherAdmins(userId) == 0) {
            throw new BusinessException("至少保留一名有效的系统管理员");
        }
        mapper.deleteRoles(userId);
        if (!roleIds.isEmpty()) mapper.insertRoles(userId, roleIds);
    }

    private List<Long> validateRoles(List<Long> ids, Long operatorId) {
        List<Long> uniqueIds = List.copyOf(new LinkedHashSet<>(ids));
        if (uniqueIds.isEmpty()) return uniqueIds;
        List<Role> roles = mapper.findAvailableRoles(uniqueIds);
        if (roles.size() != uniqueIds.size()) throw new BusinessException("请选择有效且启用的角色");
        if (roles.stream().anyMatch(r -> r.getRoleCode().equals("SUPER_ADMIN")) && !isAdmin(operatorId)) {
            throw new AccessDeniedException("只有系统管理员可以分配管理员角色");
        }
        return uniqueIds;
    }

    private boolean isAdmin(Long userId) {
        return authMapper.findRoles(userId).stream().anyMatch(r -> r.getRoleCode().equals("SUPER_ADMIN"));
    }
}
