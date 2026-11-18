package com.crm.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class QuotationEvent {
    private Long id;
    private Long quotationId;
    private Long actorId;
    private String actorName;
    private String action;
    private String note;
    private LocalDateTime createdAt;
}
