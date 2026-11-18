package com.crm.entity;

import lombok.Data;

@Data
public class ProductAttributeValue {
    private Long attributeId;
    private String attributeCode;
    private String valueJson;
}
