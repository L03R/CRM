package com.crm.dto;
import jakarta.validation.constraints.*;
public record DeliveryAckRequest(@NotNull Integer orderVersion,@NotBlank @Pattern(regexp="PRODUCTION|STOCKING") String kind,@NotBlank @Size(max=500) String note) {}
