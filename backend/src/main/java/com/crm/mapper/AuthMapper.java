package com.crm.mapper;

import com.crm.entity.Permission;
import com.crm.entity.Role;
import com.crm.entity.User;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface AuthMapper {
    User findByEmployeeNo(String employeeNo);
    User findByTokenHash(String tokenHash);
    String findDeptName(Long deptId);
    List<Role> findRoles(Long userId);
    List<Permission> findGrantedPermissions(Long userId);
    List<Permission> findEnabledPermissions();
    void insertSession(@Param("userId") Long userId, @Param("tokenHash") String tokenHash,
                       @Param("expiresAt") LocalDateTime expiresAt);
    void updateLastLogin(Long userId);
    void revokeSession(String tokenHash);
    void revokeUserSessions(Long userId);
    void insertUser(User user);
    int assignAdminRole(Long userId);
}
