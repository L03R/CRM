package com.crm.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record DeliveryProgressRequest(@NotNull Integer version,@NotBlank @Pattern(regexp="PRODUCTION|STOCKING") String preparation,LocalDate expectedDate,@Size(max=1000) String exceptionNote) {}
