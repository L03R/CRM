package com.crm.vo;

import lombok.Data;
import java.time.*;
import java.util.*;

@Data
public class DeliveryEventVO {
    private Long id;
    private Long actorId;
    private String actorName;
    private String action;
    private String note;
    private LocalDateTime createdAt;
}
