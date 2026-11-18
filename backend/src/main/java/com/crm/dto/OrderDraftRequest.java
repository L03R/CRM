package com.crm.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
public record OrderDraftRequest(@Size(max=64) String customerOrderNo,@Size(max=64) String recipientName,
        @Size(max=32) String recipientPhone,@Size(max=255) String shippingAddress,LocalDate promisedDate,
        @Size(max=500) String confirmationNote, @NotNull @Min(0) Integer version, LocalDate requestedDate,
        @Pattern(regexp="INCLUDED|EXCLUDED") String taxMode,
        @Size(max=500) String paymentTerms, @Size(max=500) String deliveryTerms,
        @Size(max=255) String freightTerms, @Size(max=1000) String remark,
        @Size(min=1,max=100) List<@NotNull @Valid OrderItemRequest> items) {}
