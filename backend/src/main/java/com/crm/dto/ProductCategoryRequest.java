package com.crm.dto;

import jakarta.validation.constraints.*;

public record ProductCategoryRequest(
        @Positive Long parentId,
        @NotBlank @Size(max=64) String categoryName,
        @NotBlank @Pattern(regexp="[A-Z0-9_-]{2,64}",message="分类编码使用2-64位大写字母、数字、下划线或短横线") String categoryCode,
        @NotNull Integer sortNo,
        @NotNull @Min(0) @Max(1) Integer status
) {}
