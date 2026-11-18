package com.crm.entity;

import lombok.Data;
import java.time.*;
import java.math.BigDecimal;

@Data
public class Customer {
    private Long id;
    private Long ownerId;
    private Long poolDeptId;
    private Long createdBy;
    private Long sourceLeadId;
    private String companyName;
    private String customerType;
    private String region;
    private String source;
    private String contactName;
    private String phone;
    private String email;
    private String address;
    private String remark;
    private String relationship;
    private Boolean protectedCustomer;
    private LocalDateTime lastFollowAt;
    private LocalDateTime nextFollowAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
