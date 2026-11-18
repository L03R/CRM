package com.crm.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OperationLog {
    private Long id;
    private Long userId;
    private String employeeNo;
    private String realName;
    private String operation;
    private String httpMethod;
    private String resource;
    private LocalDateTime createdAt;
}
