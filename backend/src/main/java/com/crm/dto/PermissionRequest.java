package com.crm.dto;

import jakarta.validation.constraints.*;

public record PermissionRequest(
        @NotBlank(message = "请输入权限名称") @Size(max = 64) String permissionName,
        @NotBlank(message = "请输入权限编码") @Size(max = 128)
        @Pattern(regexp = "[a-z][a-z0-9]*(?::[A-Za-z0-9_-]+)+", message = "权限编码使用冒号分隔，如 customer:export") String permissionCode,
        @NotNull @Min(1) @Max(3) Integer permissionType,
        @Positive Long parentId,
        @Size(max = 255) String routePath,
        @Size(max = 255) String component,
        @Size(max = 64) String icon,
        @NotNull Integer sortNo,
        @NotNull @Min(0) @Max(1) Integer visible,
        @NotNull @Min(0) @Max(1) Integer status
) {}
