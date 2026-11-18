package com.crm.dto;

import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;

public record AssignOwnerRequest(
        @NotNull @Positive Long ownerId, @NotBlank @Size(max=500) String reason
) {}
