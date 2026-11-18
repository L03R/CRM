package com.crm.entity;

import lombok.Data;
import java.time.*;
import java.math.BigDecimal;

@Data
public class FollowRecord {
    private Long id;
    private Long leadId;
    private Long customerId;
    private Long contactId;
    private String contactNameSnapshot;
    private Long createdBy;
    private String method;
    private String content;
    private LocalDateTime occurredAt;
    private LocalDateTime nextFollowAt;
    private LocalDateTime createdAt;
}
