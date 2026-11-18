package com.crm.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record AfterSaleSourceRequest(Long orderId,Long shipmentId,Long contactId,@Size(max=128) String serialNo,
    @NotNull @Size(max=100) List<@NotNull @Valid Line> items) {
    public record Line(Long orderItemId,@Size(max=128) String productName,@Size(max=64) String skuCode,
                       @Size(max=2000) String specification,@NotNull @Min(1) @Max(1000000) Integer quantity) {}
}
