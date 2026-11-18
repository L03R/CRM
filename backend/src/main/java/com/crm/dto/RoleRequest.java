package com.crm.dto;

import jakarta.validation.constraints.*;

public record RoleRequest(
        @NotBlank(message = "请输入角色名称") @Size(max = 64) String roleName,
        @NotBlank(message = "请输入角色编码") @Pattern(regexp = "[A-Z0-9_]{2,64}", message = "角色编码为2-64位大写字母、数字或下划线") String roleCode,
        @NotNull @Min(1) @Max(4) Integer dataScope,
        @NotNull @Min(0) @Max(1) Integer status,
        @Size(max = 500) String remark
) {}
