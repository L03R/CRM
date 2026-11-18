package com.crm.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
public record ShipmentRequest(@NotNull Long orderId,@NotBlank @Size(max=64) String requestKey,@NotNull Integer orderVersion,
    @NotNull @PastOrPresent LocalDateTime shippedAt,@NotBlank @Size(max=64) String recipientName,@NotBlank @Size(max=32) String recipientPhone,
    @NotBlank @Size(max=255) String shippingAddress,@NotBlank @Size(max=64) String carrier,@NotBlank @Size(max=128) String trackingNo,
    @Size(max=500) String remark,@NotEmpty @Size(max=100) List<@NotNull @Valid Line> items) {
    public record Line(@NotNull Long orderItemId,@NotNull @Min(1) @Max(1000000) Integer quantity) {}
}
