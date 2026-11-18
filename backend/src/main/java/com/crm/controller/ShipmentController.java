package com.crm.controller;
import com.crm.common.*;
import com.crm.dto.*;
import com.crm.entity.*;
import com.crm.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/shipments") @RequiredArgsConstructor
public class ShipmentController {
    private final DeliveryService service;
    @GetMapping @PreAuthorize("hasAuthority('shipment:list')")
    public ApiResult<PageResult<Shipment>> page(@AuthenticationPrincipal User user,@Valid DeliveryQuery query) {return ApiResult.success(service.shipments(user,query));}
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('shipment:list')")
    public ApiResult<Shipment> detail(@PathVariable Long id,@AuthenticationPrincipal User user) {return ApiResult.success(service.shipment(id,user));}
    @PostMapping @PreAuthorize("hasAuthority('shipment:add')")
    public ApiResult<Long> create(@AuthenticationPrincipal User user,@Valid @RequestBody ShipmentRequest request) {return ApiResult.success(service.ship(request,user));}
    @PostMapping("/{id}/void") @PreAuthorize("hasAuthority('shipment:void')")
    public ApiResult<Void> correct(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody ReasonRequest request) {service.voidShipment(id,request,user);return ApiResult.success(null);}
}
