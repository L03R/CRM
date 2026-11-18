package com.crm.controller;

import com.crm.common.*;
import com.crm.entity.User;
import com.crm.service.*;
import com.crm.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {
    private final WorkflowService service;
    private final OpportunityService opportunities;
    @GetMapping("/owners") @PreAuthorize("hasAuthority('clue:assign') or hasAuthority('customer:assign')")
    public ApiResult<List<WorkflowOwnerVO>> owners(@AuthenticationPrincipal User user,@RequestParam(defaultValue="LEAD") String kind){return ApiResult.success(service.assignableOwners(user,kind));}
    @GetMapping("/departments") @PreAuthorize("hasAuthority('clue:add') or hasAuthority('clue:edit')")
    public ApiResult<List<WorkflowDepartmentVO>> departments(){return ApiResult.success(service.departments());}
    @GetMapping("/customer-options") @PreAuthorize("hasAuthority('business:add')")
    public ApiResult<PageResult<CustomerVO>> customerOptions(@AuthenticationPrincipal User user,@RequestParam(required=false) String keyword){return ApiResult.success(opportunities.customerOptions(user,keyword));}
}
