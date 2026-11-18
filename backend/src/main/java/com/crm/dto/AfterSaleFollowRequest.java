package com.crm.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
public record AfterSaleFollowRequest(@NotNull Integer version,@NotBlank @Size(max=2000) String content,
    @NotNull @PastOrPresent LocalDateTime occurredAt,LocalDateTime nextFollowAt) {}
