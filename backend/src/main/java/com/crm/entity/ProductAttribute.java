package com.crm.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class ProductAttribute {
    private Long id;
    private Long categoryId;
    private String attributeScope;
    private String attributeCode;
    private String attributeName;
    private String inputType;
    @JsonIgnore
    private String optionsJson;
    private String unit;
    private Boolean required;
    private String conditionCode;
    private String conditionValue;
    private Integer decimalPlaces;
    private Integer sortNo;
    private Integer status;
}
