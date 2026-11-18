package com.crm.controller;
import com.crm.common.*;
import com.crm.dto.*;
import com.crm.entity.User;
import com.crm.service.DeliveryService;
import com.crm.vo.DeliveryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/deliveries") @RequiredArgsConstructor
public class DeliveryController {
    private final DeliveryService service;
    @GetMapping @PreAuthorize("hasAuthority('delivery:progress:list')")
    public ApiResult<PageResult<DeliveryVO>> page(@AuthenticationPrincipal User user,@Valid DeliveryQuery query) {return ApiResult.success(service.page(user,query));}
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('delivery:progress:list')")
    public ApiResult<DeliveryVO> detail(@PathVariable Long id,@AuthenticationPrincipal User user) {return ApiResult.success(service.detail(id,user));}
    @GetMapping("/{id}/people") @PreAuthorize("hasAuthority('delivery:assign')")
    public ApiResult<Map<String,List<Map<String,Object>>>> people(@PathVariable Long id,@AuthenticationPrincipal User user) {return ApiResult.success(service.options(id,user));}
    @PutMapping("/{id}/assignment") @PreAuthorize("hasAuthority('delivery:assign')")
    public ApiResult<Void> assign(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody DeliveryAssignRequest request) {service.assign(id,request,user);return ApiResult.success(null);}
    @PutMapping("/{id}/progress") @PreAuthorize("hasAnyAuthority('delivery:production','delivery:stock')")
    public ApiResult<Void> progress(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody DeliveryProgressRequest request) {service.progress(id,request,user);return ApiResult.success(null);}
    @PostMapping("/{id}/acknowledgement") @PreAuthorize("hasAnyAuthority('delivery:production','delivery:stock')")
    public ApiResult<Void> acknowledge(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody DeliveryAckRequest request) {service.acknowledge(id,request,user);return ApiResult.success(null);}
    @PostMapping("/{id}/complete") @PreAuthorize("hasAuthority('delivery:complete')")
    public ApiResult<Void> complete(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody DeliveryCompleteRequest request) {service.complete(id,request,user);return ApiResult.success(null);}
}
