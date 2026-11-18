package com.crm.entity;

import lombok.Data;
import java.time.*;
import java.math.BigDecimal;

@Data
public class Lead {
    private String companyName;
    private String customerType;
    private String region;
    private String source;
    private String contactName;
    private String phone;
    private String email;
    private String contactKey;
    private String demand;
    private String status;
    private String closeReason;
    private Long id;
    private Long targetDeptId;
    private Long ownerId;
    private Long createdBy;
    private LocalDateTime lastFollowAt;
    private LocalDateTime nextFollowAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
