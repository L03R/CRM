package com.crm.dto;

import jakarta.validation.constraints.*;

public record ProductStatusRequest(@NotNull @Min(0) @Max(1) Integer status) {}
