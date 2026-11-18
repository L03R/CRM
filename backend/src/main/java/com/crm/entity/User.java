package com.crm.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class User {
    private Long id;
    private String employeeNo;
    private String realName;
    @JsonIgnore
    private String passwordHash;
    private Long deptId;
    private Integer status;
    private Integer employmentStatus;
    private Integer deleted;
    private String phone;
    private String email;
    private java.time.LocalDate hireDate;
    private String remark;

    public boolean canLogin() {
        return status == 1 && employmentStatus == 1 && deleted == 0;
    }
}
