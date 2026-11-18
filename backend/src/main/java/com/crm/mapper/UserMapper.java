package com.crm.mapper;

import com.crm.entity.Role;
import com.crm.entity.User;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface UserMapper {
    Long lockEnabledDepartment(Long id);
    void insert(User user);
    User lockUser(Long id);
    void update(User user);
    void updateState(User user);
    void updatePassword(@Param("id") Long id,@Param("hash") String hash);
    void clearLeader(Long userId);
    List<com.crm.vo.EmployeeOptionVO> options();
    Long lockAdminRole();
    List<Role> findAvailableRoles(@Param("ids") List<Long> ids);
    int countOtherAdmins(Long userId);
    void deleteRoles(Long userId);
    void insertRoles(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);
}
