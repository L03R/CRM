package com.crm.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
public record QuotationRequest(@NotNull Long customerId,@NotNull Long opportunityId,@NotNull Long contactId,
        @NotBlank @Size(max=128) String title,@NotNull LocalDate quoteDate,@NotNull LocalDate validUntil,
        @NotNull @Pattern(regexp="INCLUDED|EXCLUDED") String taxMode,
        @NotBlank @Size(max=500) String paymentTerms,@NotBlank @Size(max=500) String deliveryTerms,
        @Size(max=255) String freightTerms,@Size(max=1000) String remark,
        @NotEmpty @Size(max=100) List<@NotNull @Valid QuotationItemRequest> items) {}
