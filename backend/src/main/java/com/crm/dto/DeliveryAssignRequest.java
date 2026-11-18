package com.crm.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record DeliveryAssignRequest(@NotNull Integer version,@NotNull Long managerId,Long productionId,Long warehouseId,LocalDate expectedDate,@Size(max=1000) String exceptionNote) {}
