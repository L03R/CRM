package com.crm.dto;

import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;

public record ReasonRequest(
        @NotBlank @Size(max=500) String reason
) {}
