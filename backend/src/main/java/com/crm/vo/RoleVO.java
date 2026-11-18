package com.crm.vo;

import lombok.Data;
import java.util.List;

@Data
public class RoleVO {
    private Long id;
    private String roleName;
    private String roleCode;
    private Integer dataScope;
    private Integer status;
    private Integer isBuiltin;
    private String remark;
    private Integer members;
    private List<Long> permissionIds;
}
