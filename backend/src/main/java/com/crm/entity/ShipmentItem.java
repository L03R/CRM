package com.crm.entity;

import lombok.Data;
import java.time.*;
import java.util.*;

@Data
public class ShipmentItem {
    private Long id;
    private Long shipmentId;
    private Long orderId;
    private Long orderItemId;
    private String productName;
    private String skuCode;
    private String specification;
    private String unit;
    private Integer quantity;
}
