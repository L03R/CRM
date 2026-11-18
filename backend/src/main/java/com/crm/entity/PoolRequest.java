package com.crm.entity;

import lombok.Data;
import java.time.*;
import java.math.BigDecimal;

@Data
public class PoolRequest {
    private Long id;
    private Long customerId;
    private Long requestedBy;
    private Long reviewedBy;
    private String reason;
    private String status;
    private String reviewNote;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
