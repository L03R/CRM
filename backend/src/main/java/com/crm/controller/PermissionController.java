package com.crm.controller;

import com.crm.common.ApiResult;
import com.crm.dto.PermissionRequest;
import com.crm.dto.PermissionStatusRequest;
import com.crm.service.PermissionManageService;
import com.crm.vo.PermissionTreeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/permissions")
@RequiredArgsConstructor
public class PermissionController {
    private final PermissionManageService service;

    @GetMapping("/tree")
    @PreAuthorize("@permissionManageService.isAdmin(authentication.principal.id) and hasAuthority('system:permission:list')")
    public ApiResult<List<PermissionTreeVO>> tree(@RequestParam(required = false) String keyword, @RequestParam(required = false) Integer type) {
        return ApiResult.success(service.tree(keyword, type));
    }

    @GetMapping("/components")
    @PreAuthorize("@permissionManageService.isAdmin(authentication.principal.id) and hasAuthority('system:permission:list')")
    public ApiResult<Map<String, String>> components() { return ApiResult.success(service.components()); }

    @PostMapping
    @PreAuthorize("@permissionManageService.isAdmin(authentication.principal.id) and hasAuthority('system:permission:add')")
    public ApiResult<Long> create(@Valid @RequestBody PermissionRequest request) { return ApiResult.success(service.create(request)); }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionManageService.isAdmin(authentication.principal.id) and hasAuthority('system:permission:edit')")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody PermissionRequest request) {
        service.update(id, request);
        return ApiResult.success(null);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@permissionManageService.isAdmin(authentication.principal.id) and hasAuthority('system:permission:status')")
    public ApiResult<Void> status(@PathVariable Long id, @Valid @RequestBody PermissionStatusRequest request) {
        service.status(id, request.status());
        return ApiResult.success(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionManageService.isAdmin(authentication.principal.id) and hasAuthority('system:permission:delete')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResult.success(null);
    }
}
