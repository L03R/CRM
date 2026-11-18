package com.crm.dto;

import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;

public record OpportunityRequest(
        @NotNull @Positive Long customerId, @NotBlank @Size(max=128) String title,
        @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal expectedAmount,
        LocalDate expectedCloseDate, @NotBlank @Pattern(regexp="INITIAL|QUALIFIED|PROPOSAL|NEGOTIATION|WON|LOST") String stage,
        @Size(max=2000) String remark, @Size(max=500) String closeReason
) {}
