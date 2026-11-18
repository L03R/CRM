package com.crm.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record QuotationItemRequest(@NotNull Long skuId,@NotNull @Min(1) @Max(1000000) Integer quantity,
        @NotNull @DecimalMin("0") @Digits(integer=8,fraction=2) BigDecimal unitPrice,@Size(max=500) String requirements) {}
