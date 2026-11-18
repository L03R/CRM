package com.crm.entity;

import lombok.Data;
import java.time.*;
import java.math.BigDecimal;

@Data
public class Opportunity {
    private Long id;
    private Long customerId;
    private Long createdBy;
    private String title;
    private String stage;
    private String remark;
    private String closeReason;
    private BigDecimal expectedAmount;
    private LocalDate expectedCloseDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
