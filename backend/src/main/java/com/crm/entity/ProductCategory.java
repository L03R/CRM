package com.crm.entity;

import lombok.Data;

@Data
public class ProductCategory {
    private Long id;
    private Long parentId;
    private String categoryName;
    private String categoryCode;
    private Integer sortNo;
    private Integer status;
}
