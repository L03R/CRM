package com.crm.dto;
import jakarta.validation.constraints.*;
public record DeliveryCompleteRequest(@NotNull Integer version,@NotBlank @Size(max=500) String note) {}
