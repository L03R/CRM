package com.crm.dto;

import jakarta.validation.constraints.*;

public record EmployeeStateRequest(@NotNull @Min(0) @Max(1) Integer status,
                                   @NotNull @Min(0) @Max(1) Integer employmentStatus) {}
