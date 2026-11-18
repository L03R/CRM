package com.crm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record AssignRolesRequest(
        @NotNull(message = "角色列表不能为空，可传空数组") List<@NotNull @Positive Long> roleIds
) {}
