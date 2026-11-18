package com.crm.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
public record AfterSaleRequest(@NotNull Long customerId,@NotBlank @Size(max=128) String title,
    @NotBlank @Pattern(regexp="QUALITY|TRANSPORT|WRONG_MISSING|CONSULTATION|OTHER") String issueType,
    @NotBlank @Size(max=2000) String description,@Size(max=500) String expectation,
    @NotBlank @Size(max=64) String reporterName,@NotBlank @Size(max=128) String reporterContact,
    @NotNull @PastOrPresent LocalDateTime reportedAt,LocalDateTime nextFollowAt,
    @NotNull @Valid AfterSaleSourceRequest source) {}
