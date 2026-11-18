package com.crm.dto;

import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;

public record FollowRequest(
        @NotBlank @Pattern(regexp="PHONE|WECHAT|EMAIL|VISIT|EXHIBITION|OTHER") String method,
        @NotBlank @Size(max=2000) String content,
        @NotNull @PastOrPresent LocalDateTime occurredAt,
        @FutureOrPresent LocalDateTime nextFollowAt,
        Long contactId
) {}
