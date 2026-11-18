package com.crm.dto;

import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;

public record PoolReviewRequest(
        @NotNull Boolean approve, @NotBlank @Size(max=500) String note
) {}
