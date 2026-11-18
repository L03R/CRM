package com.crm.dto;

import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;

public record LeadRequest(
        @NotBlank @Size(max=128) String companyName,
        @NotBlank @Pattern(regexp="DEALER|STORE|BRAND|TRADER|TRAINING|OTHER") String customerType,
        @NotBlank @Size(max=100) String region,
        @NotBlank @Size(max=64) String source,
        @NotBlank @Size(max=64) String contactName,
        @Size(max=32) String phone, @Email @Size(max=128) String email,
        @NotBlank @Size(max=2000) String demand,
        @NotNull @Positive Long targetDeptId,
        LocalDateTime nextFollowAt
) {}
