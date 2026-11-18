package com.crm.entity;

import lombok.Data;

@Data
public class Permission {
    private Long id;
    private Long parentId;
    private String permissionName;
    private String permissionCode;
    private Integer permissionType;
    private String routePath;
    private String component;
    private String httpMethod;
    private String apiPath;
    private String icon;
    private Integer sortNo;
    private Integer visible;
    private Integer status;
}
