package com.crm.entity;

import lombok.Data;
import java.time.*;
import java.math.BigDecimal;

@Data
public class OwnershipEvent {
    private Long id;
    private Long resourceId;
    private Long fromUserId;
    private Long toUserId;
    private Long actorId;
    private String resourceType;
    private String action;
    private String reason;
    private String fromName;
    private String toName;
    private String actorName;
    private LocalDateTime createdAt;
}
