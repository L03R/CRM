package com.crm.vo;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class EmployeeVO {
    private Long id;
    private String employeeNo;
    private String realName;
    private Long deptId;
    private String deptName;
    private String phone;
    private String email;
    private Integer status;
    private Integer employmentStatus;
    private LocalDate hireDate;
    private LocalDateTime lastLoginAt;
    private List<Long> roleIds;
    private String remark;
}
