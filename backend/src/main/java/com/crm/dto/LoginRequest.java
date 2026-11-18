package com.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "请输入员工编号") @Size(max = 32) String employeeNo,
        @NotBlank(message = "请输入密码") @Size(max = 72) String password
) {}
