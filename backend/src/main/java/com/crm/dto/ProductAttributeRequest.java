package com.crm.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record ProductAttributeRequest(
        @NotBlank @Pattern(regexp="MODEL|SKU") String attributeScope,
        @NotBlank @Pattern(regexp="[a-z][a-z0-9_]{0,63}",message="属性编码使用小写字母开头的小写字母、数字和下划线") String attributeCode,
        @NotBlank @Size(max=64) String attributeName,
        @NotBlank @Pattern(regexp="TEXT|NUMBER|SELECT|MULTI") String inputType,
        @NotNull @Size(max=100) List<@NotBlank @Size(max=64) String> options,
        @Size(max=16) String unit,
        @NotNull Boolean required,
        @Size(max=64) String conditionCode,
        @Size(max=64) String conditionValue,
        @NotNull @Min(0) @Max(6) Integer decimalPlaces,
        @NotNull @Min(0) @Max(9999) Integer sortNo,
        @NotNull @Min(0) @Max(1) Integer status
) {}
