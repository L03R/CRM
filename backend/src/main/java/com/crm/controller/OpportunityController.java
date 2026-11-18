package com.crm.controller;

import com.crm.common.*;
import com.crm.dto.*;
import com.crm.entity.User;
import com.crm.service.OpportunityService;
import com.crm.vo.OpportunityVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/opportunities")
@RequiredArgsConstructor
public class OpportunityController {
    private final OpportunityService service;
    @GetMapping @PreAuthorize("hasAuthority('business:list')")
    public ApiResult<PageResult<OpportunityVO>> page(@AuthenticationPrincipal User user,@RequestParam(required=false) String keyword,@RequestParam(required=false) Long customerId,@RequestParam(required=false) String stage,@Valid PageQuery page){return ApiResult.success(service.page(user,keyword,customerId,stage,page));}
    @PostMapping @PreAuthorize("hasAuthority('business:add')")
    public ApiResult<Long> create(@Valid @RequestBody OpportunityRequest request,@AuthenticationPrincipal User user){return ApiResult.success(service.create(request,user));}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('business:edit')")
    public ApiResult<Void> update(@PathVariable Long id,@Valid @RequestBody OpportunityRequest request,@AuthenticationPrincipal User user){service.update(id,request,user);return ApiResult.success(null);}
}
