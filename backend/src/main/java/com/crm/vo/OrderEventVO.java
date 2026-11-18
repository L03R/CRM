package com.crm.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OrderEventVO {
    private Long id;
    private Long orderId;
    private Long actorId;
    private String actorName;
    private String action;
    private String note;
    @JsonIgnore private String beforeJson;
    @JsonIgnore private String afterJson;
    private OrderRecord beforeContent;
    private OrderRecord afterContent;
    private LocalDateTime createdAt;
}
