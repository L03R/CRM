package com.crm.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

public record ProductRequest(
        @NotNull @Positive Long categoryId,
        @NotBlank @Pattern(regexp="[A-Z0-9_-]{2,64}",message="型号编码使用2-64位大写字母、数字、下划线或短横线") String modelCode,
        @NotBlank @Size(max=128) String productName,
        @NotBlank @Size(max=64) String brand,
        @Size(max=64) String series,
        @Size(max=2000) String specifications,
        @Size(max=500) String imageUrl,
        @NotNull @Min(1) @Max(3) Integer supplyType,
        @NotBlank @Size(max=16) String unit,
        @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal retailPrice,
        @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal channelPrice,
        @Min(0) Integer leadDays,
        @Size(max=100) Map<String,Object> attributes
) {}
