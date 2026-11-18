package com.crm.entity;
import lombok.Data;
import java.time.*;
import java.util.*;
@Data
public class AfterSaleImage {
    private Long id;
    private Long caseId;
    private Long actorId;
    private String filename;
    private String mimeType;
    private LocalDateTime createdAt;
    @com.fasterxml.jackson.annotation.JsonIgnore private byte[] imageData;
}
