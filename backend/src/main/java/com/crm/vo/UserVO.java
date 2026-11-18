package com.crm.vo;

import com.crm.entity.Role;
import java.util.List;

public record UserVO(Long id, String employeeNo, String realName, Long deptId,
                     String deptName, List<Role> roles) {}
