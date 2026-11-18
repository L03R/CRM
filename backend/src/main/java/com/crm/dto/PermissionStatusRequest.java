package com.crm.dto;

import jakarta.validation.constraints.*;

public record PermissionStatusRequest(@NotNull @Min(0) @Max(1) Integer status) {}
