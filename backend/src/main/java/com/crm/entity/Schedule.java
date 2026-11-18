package com.crm.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class Schedule {
    private Long id;
    private Long userId;
    private String title;
    private String description;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime remindAt;
    private String priority;
    private String status;
}
