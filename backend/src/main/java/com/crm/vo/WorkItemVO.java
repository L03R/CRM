package com.crm.vo;
import java.time.LocalDateTime;
public record WorkItemVO(Long id, String kind, String title, String reason, LocalDateTime dueAt) {}
