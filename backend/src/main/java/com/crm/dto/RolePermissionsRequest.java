package com.crm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record RolePermissionsRequest(
        @NotNull(message = "权限列表不能为空，可传空数组") List<@NotNull @Positive Long> permissionIds
) {}
