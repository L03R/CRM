package com.crm.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
public record AfterSaleTransferRequest(@NotNull Integer version,@NotBlank @Size(max=128) String handlerName,
    @NotBlank @Size(max=128) String handlerContact,Long helperId,@NotBlank @Size(max=1000) String note,LocalDateTime nextFollowAt) {}
