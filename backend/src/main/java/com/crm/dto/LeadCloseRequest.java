package com.crm.dto;

import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;

public record LeadCloseRequest(
        @NotBlank @Pattern(regexp="INVALID|CLOSED") String status, @NotBlank @Size(max=500) String reason
) {}
