package com.crm.entity;

import lombok.Data;
import java.time.*;
import java.util.*;

@Data
public class OrderDelivery {
    private Long orderId;
    private Long managerId;
    private Long productionId;
    private Long warehouseId;
    private String preparation;
    private String exceptionNote;
    private Boolean started;
    private LocalDate expectedDate;
    private Integer version;
    private Integer productionAck;
    private Integer warehouseAck;
    private LocalDateTime updatedAt;
}
