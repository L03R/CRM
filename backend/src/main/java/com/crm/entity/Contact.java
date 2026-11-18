package com.crm.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class Contact {
    private Long id;
    private Long customerId;
    private String contactName;
    private String jobTitle;
    private String phone;
    private String landline;
    private String wechat;
    private String email;
    private Boolean isPrimary;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
