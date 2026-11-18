package com.crm.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Dept {
    private Long id;
    private Long parentId;
    private String deptName;
    private String deptCode;
    private Long leaderUserId;
    private Integer sortNo;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
