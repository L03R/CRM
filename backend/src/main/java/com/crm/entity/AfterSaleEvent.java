package com.crm.entity;
import lombok.Data;
import java.time.*;
import java.util.*;
@Data
public class AfterSaleEvent {
    private Long id;
    private Long caseId;
    private Long actorId;
    private String actorName;
    private String action;
    private String content;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
