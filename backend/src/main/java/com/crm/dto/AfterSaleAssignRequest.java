package com.crm.dto;
import jakarta.validation.constraints.*;
public record AfterSaleAssignRequest(@NotNull Integer version,@NotNull Long ownerId,@NotBlank @Size(max=500) String note) {}
