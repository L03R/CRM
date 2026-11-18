package com.crm.entity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.math.BigDecimal;
import java.time.*;
@Data
public class SalesOrder {
    private Long id;
    private String orderNo;
    private Long quotationId;
    private Long customerId;
    private Long opportunityId;
    private Long createdBy;
    private String status;
    private BigDecimal totalAmount;
    @JsonIgnore private String snapshotJson;
    private String customerOrderNo;
    private String recipientName;
    private String recipientPhone;
    private String shippingAddress;
    private LocalDate promisedDate;
    private String confirmationNote;
    private String cancelReason;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
    private LocalDate requestedDate;
    private String taxMode;
    private String paymentTerms;
    private String deliveryTerms;
    private String freightTerms;
    private String remark;
    private Boolean deleted;
    private java.time.LocalDateTime deletedAt;
    private Long deletedBy;
}
