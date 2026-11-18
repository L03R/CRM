package com.crm.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

public record ProductSkuRequest(
        @NotBlank @Pattern(regexp="[A-Z0-9_-]{2,64}",message="SKU编码使用2-64位大写字母、数字、下划线或短横线") String skuCode,
        @Size(max=32) String color,
        @Size(max=32) String handStyle,
        @Size(max=128) String packaging,
        @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal salePrice,
        @Size(max=100) Map<String,Object> attributes
) {}
