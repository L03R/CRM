package com.crm.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
public record ScheduleRequest(
        @NotBlank @Size(max=128) String title,
        @NotNull @Size(max=1000) String description,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt,
        LocalDateTime remindAt,
        @NotNull @Pattern(regexp="NORMAL|HIGH") String priority,
        @NotNull @Pattern(regexp="PENDING|DONE|CANCELLED") String status) {}
