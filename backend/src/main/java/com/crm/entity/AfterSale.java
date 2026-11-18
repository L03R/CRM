package com.crm.entity;
import lombok.Data;
import java.time.*;
import java.util.*;
@Data
public class AfterSale {
    private Long id;
    private Long customerId;
    private Long contactId;
    private Long orderId;
    private Long shipmentId;
    private Long ownerId;
    private Long helperId;
    private Long createdBy;
    private String caseNo;
    private String title;
    private String issueType;
    private String description;
    private String expectation;
    private String reporterName;
    private String reporterContact;
    private String serialNo;
    private String status;
    private String handlerName;
    private String handlerContact;
    private String latestFeedback;
    private String resolutionType;
    private String resultNote;
    private String customerFeedback;
    private String externalNo;
    private LocalDateTime reportedAt;
    private LocalDateTime nextFollowAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
}
