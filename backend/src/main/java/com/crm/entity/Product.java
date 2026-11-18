package com.crm.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Product {
    private Long id;
    private Long categoryId;
    private String modelCode;
    private String productName;
    private String brand;
    private String series;
    private String specifications;
    private String imageUrl;
    private Integer supplyType;
    private String unit;
    private BigDecimal retailPrice;
    private BigDecimal channelPrice;
    private Integer leadDays;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
