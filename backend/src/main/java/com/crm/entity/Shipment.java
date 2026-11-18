package com.crm.entity;

import lombok.Data;
import java.time.*;
import java.util.*;

@Data
public class Shipment {
    private Long id;
    private Long orderId;
    private Long actorId;
    private String requestKey;
    private String shipmentNo;
    private String status;
    private String recipientName;
    private String recipientPhone;
    private String shippingAddress;
    private String carrier;
    private String trackingNo;
    private String remark;
    private String voidReason;
    private String actorName;
    private String orderNo;
    private String customerName;
    private LocalDateTime shippedAt;
    private LocalDateTime createdAt;
    private List<ShipmentItem> items=List.of();
}
