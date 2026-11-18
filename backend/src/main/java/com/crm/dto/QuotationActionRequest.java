package com.crm.dto;
import jakarta.validation.constraints.*;
public record QuotationActionRequest(@NotNull @Pattern(regexp="SEND|ACCEPT|REJECT|VOID|FINAL") String action,
        @NotBlank @Size(max=500) String note) {}
