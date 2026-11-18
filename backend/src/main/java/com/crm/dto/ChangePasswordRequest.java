package com.crm.dto;

import jakarta.validation.constraints.*;

public record ChangePasswordRequest(
        @NotBlank @Size(max=72) String oldPassword,
        @NotBlank @Size(min=8,max=72) @Pattern(regexp="(?s)(?=.*[A-Za-z])(?=.*[0-9]).*",message="新密码至少包含字母和数字") String newPassword
) {}
