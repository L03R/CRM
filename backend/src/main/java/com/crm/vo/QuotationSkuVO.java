package com.crm.vo;
import java.math.BigDecimal;
public record QuotationSkuVO(Long id,String skuCode,String productName,String modelCode,String unit,BigDecimal salePrice,String specification) {}
