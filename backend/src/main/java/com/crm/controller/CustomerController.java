package com.crm.controller;

import com.crm.common.*;
import com.crm.dto.*;
import com.crm.entity.*;
import com.crm.service.CustomerService;
import com.crm.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService service;
    @GetMapping @PreAuthorize("hasAuthority('customer:list')")
    public ApiResult<PageResult<CustomerVO>> page(@AuthenticationPrincipal User user,@RequestParam(required=false) String keyword,@Valid PageQuery page){return ApiResult.success(service.page(user,false,keyword,page));}
    @GetMapping("/pool") @PreAuthorize("hasAuthority('customer:pool:list')")
    public ApiResult<PageResult<CustomerVO>> pool(@AuthenticationPrincipal User user,@RequestParam(required=false) String keyword,@Valid PageQuery page){return ApiResult.success(service.page(user,true,keyword,page));}
    @GetMapping("/follows") @PreAuthorize("hasAuthority('customer:follow:list')")
    public ApiResult<PageResult<FollowVO>> follows(@AuthenticationPrincipal User user,@RequestParam(required=false) String keyword,@RequestParam(required=false) String due,@Valid PageQuery page){return ApiResult.success(service.followPage(user,keyword,due,page));}
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('customer:list') or hasAuthority('customer:pool:list')")
    public ApiResult<CustomerVO> detail(@PathVariable Long id,@AuthenticationPrincipal User user){return ApiResult.success(service.detail(id,user));}
    @GetMapping("/{id}/follows") @PreAuthorize("hasAuthority('customer:list') or hasAuthority('customer:pool:list')")
    public ApiResult<List<FollowVO>> history(@PathVariable Long id,@AuthenticationPrincipal User user){return ApiResult.success(service.follows(id,user));}
    @GetMapping("/{id}/events") @PreAuthorize("hasAuthority('customer:list') or hasAuthority('customer:pool:list')")
    public ApiResult<List<OwnershipEvent>> events(@PathVariable Long id,@AuthenticationPrincipal User user){return ApiResult.success(service.events(id,user));}
    @GetMapping("/{id}/pool-requests") @PreAuthorize("hasAuthority('customer:list') or hasAuthority('customer:pool:list')")
    public ApiResult<List<PoolRequest>> requests(@PathVariable Long id,@AuthenticationPrincipal User user){return ApiResult.success(service.requests(id,user));}
    @PostMapping @PreAuthorize("hasAuthority('customer:add')")
    public ApiResult<Long> create(@Valid @RequestBody CustomerRequest request,@AuthenticationPrincipal User user){return ApiResult.success(service.create(request,user));}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('customer:edit')")
    public ApiResult<Void> update(@PathVariable Long id,@Valid @RequestBody CustomerRequest request,@AuthenticationPrincipal User user){service.update(id,request,user);return ApiResult.success(null);}
    @PostMapping("/{id}/follows") @PreAuthorize("hasAuthority('customer:follow:add')")
    public ApiResult<Void> follow(@PathVariable Long id,@Valid @RequestBody FollowRequest request,@AuthenticationPrincipal User user){service.follow(id,request,user);return ApiResult.success(null);}
    @PostMapping("/{id}/assign") @PreAuthorize("hasAuthority('customer:assign')")
    public ApiResult<Void> assign(@PathVariable Long id,@Valid @RequestBody AssignOwnerRequest request,@AuthenticationPrincipal User user){service.assign(id,request,user);return ApiResult.success(null);}
    @PostMapping("/{id}/pool-requests") @PreAuthorize("hasAuthority('customer:pool:request')")
    public ApiResult<Long> requestPool(@PathVariable Long id,@Valid @RequestBody ReasonRequest request,@AuthenticationPrincipal User user){return ApiResult.success(service.requestPool(id,request,user));}
    @PostMapping("/{id}/pool-requests/{requestId}/review") @PreAuthorize("hasAuthority('customer:pool:approve')")
    public ApiResult<Void> reviewPool(@PathVariable Long id,@PathVariable Long requestId,@Valid @RequestBody PoolReviewRequest request,@AuthenticationPrincipal User user){service.reviewPool(id,requestId,request,user);return ApiResult.success(null);}
    @PostMapping("/{id}/claim") @PreAuthorize("hasAuthority('customer:pool:claim') and hasAuthority('customer:list')")
    public ApiResult<Void> claim(@PathVariable Long id,@AuthenticationPrincipal User user){service.claim(id,user);return ApiResult.success(null);}
}
