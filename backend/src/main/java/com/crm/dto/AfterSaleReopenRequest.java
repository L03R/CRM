package com.crm.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
public record AfterSaleReopenRequest(@NotNull Integer version,@NotBlank @Size(max=500) String note,LocalDateTime nextFollowAt) {}
