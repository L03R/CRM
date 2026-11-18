package com.crm.dto;

import jakarta.validation.constraints.*;

public record OrderCancelRequest(@NotNull @Min(0) Integer version,
                                 @NotBlank @Size(max=500) String reason) {}
