package com.crm.controller;

import com.crm.common.*;
import com.crm.dto.*;
import com.crm.entity.*;
import com.crm.service.LeadService;
import com.crm.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
public class LeadController {
    private final LeadService service;
    @GetMapping @PreAuthorize("hasAuthority('clue:list')")
    public ApiResult<PageResult<LeadVO>> page(@AuthenticationPrincipal User user,@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,@Valid PageQuery page){return ApiResult.success(service.page(user,keyword,status,page));}
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('clue:list')")
    public ApiResult<LeadVO> detail(@PathVariable Long id,@AuthenticationPrincipal User user){return ApiResult.success(service.detail(id,user));}
    @GetMapping("/{id}/follows") @PreAuthorize("hasAuthority('clue:list')")
    public ApiResult<List<FollowVO>> follows(@PathVariable Long id,@AuthenticationPrincipal User user){return ApiResult.success(service.follows(id,user));}
    @GetMapping("/{id}/events") @PreAuthorize("hasAuthority('clue:list')")
    public ApiResult<List<OwnershipEvent>> events(@PathVariable Long id,@AuthenticationPrincipal User user){return ApiResult.success(service.events(id,user));}
    @PostMapping @PreAuthorize("hasAuthority('clue:add')")
    public ApiResult<Long> create(@Valid @RequestBody LeadRequest request,@AuthenticationPrincipal User user){return ApiResult.success(service.create(request,user));}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('clue:edit')")
    public ApiResult<Void> update(@PathVariable Long id,@Valid @RequestBody LeadRequest request,@AuthenticationPrincipal User user){service.update(id,request,user);return ApiResult.success(null);}
    @PostMapping("/{id}/assign") @PreAuthorize("hasAuthority('clue:assign')")
    public ApiResult<Void> assign(@PathVariable Long id,@Valid @RequestBody AssignOwnerRequest request,@AuthenticationPrincipal User user){service.assign(id,request,user);return ApiResult.success(null);}
    @PostMapping("/{id}/follows") @PreAuthorize("hasAuthority('clue:follow')")
    public ApiResult<Void> follow(@PathVariable Long id,@Valid @RequestBody FollowRequest request,@AuthenticationPrincipal User user){service.follow(id,request,user);return ApiResult.success(null);}
    @PostMapping("/{id}/return") @PreAuthorize("hasAuthority('clue:return')")
    public ApiResult<Void> returnToQueue(@PathVariable Long id,@Valid @RequestBody ReasonRequest request,@AuthenticationPrincipal User user){service.returnToQueue(id,request,user);return ApiResult.success(null);}
    @PostMapping("/{id}/close") @PreAuthorize("hasAuthority('clue:close')")
    public ApiResult<Void> close(@PathVariable Long id,@Valid @RequestBody LeadCloseRequest request,@AuthenticationPrincipal User user){service.close(id,request,user);return ApiResult.success(null);}
    @PostMapping("/{id}/convert") @PreAuthorize("hasAuthority('clue:convert')")
    public ApiResult<Long> convert(@PathVariable Long id,@AuthenticationPrincipal User user){return ApiResult.success(service.convert(id,user));}
}
