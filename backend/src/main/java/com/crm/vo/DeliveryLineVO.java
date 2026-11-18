package com.crm.vo;

import lombok.Data;
import java.time.*;
import java.util.*;

@Data
public class DeliveryLineVO {
    private Long id;
    private Long skuId;
    private String productName;
    private String skuCode;
    private String specification;
    private String requirements;
    private String unit;
    private Integer quantity;
    private Integer shippedQuantity;
}
