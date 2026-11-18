package com.crm.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record UpdateEmployeeRequest(
        @NotBlank String employeeNo,
        @NotBlank @Size(max=64) String realName,
        @NotNull @Positive Long deptId,
        @Size(max=20) String phone,
        @Email @Size(max=128) String email,
        LocalDate hireDate,
        @Size(max=500) String remark
) {}
