package com.crm.entity;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class QuotationItem {
    private Long id;
    private Long quotationId;
    private Long skuId;
    private Integer sortNo;
    private String productName;
    private String modelCode;
    private String skuCode;
    private String unit;
    private String specification;
    private String requirements;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineAmount;
}
