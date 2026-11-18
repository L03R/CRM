package com.crm.controller;

import com.crm.common.*;
import com.crm.dto.PageQuery;
import com.crm.entity.OperationLog;
import com.crm.service.OperationLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/logs")
@RequiredArgsConstructor
public class OperationLogController {
    private final OperationLogService service;
    @GetMapping
    @PreAuthorize("hasAuthority('system:log:list') and @permissionManageService.isAdmin(authentication.principal.id)")
    public ApiResult<PageResult<OperationLog>> page(@RequestParam(required=false) String keyword,@Valid PageQuery page) {return ApiResult.success(service.page(keyword,page));}
}
