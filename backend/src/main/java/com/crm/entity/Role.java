package com.crm.entity;

import lombok.Data;

@Data
public class Role {
    private Long id;
    private String roleName;
    private String roleCode;
    private Integer dataScope;
    private Integer status;
    private Integer isBuiltin;
    private String remark;
}
