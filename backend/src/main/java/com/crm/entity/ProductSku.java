package com.crm.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ProductSku {
    private Long id;
    private Long productId;
    private String skuCode;
    private String color;
    private String handStyle;
    private String packaging;
    private BigDecimal salePrice;
    private Integer status;
    private Map<String,Object> attributes = Map.of();
    private List<String> missingAttributes = List.of();
}
