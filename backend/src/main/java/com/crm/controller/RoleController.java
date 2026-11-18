package com.crm.controller;

import com.crm.common.ApiResult;
import com.crm.dto.RolePermissionsRequest;
import com.crm.dto.RoleRequest;
import com.crm.entity.Permission;
import com.crm.service.RoleService;
import com.crm.service.SystemQueryService;
import com.crm.vo.RoleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService service;
    private final SystemQueryService queries;

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('system:role:list') or hasAuthority('system:employee:list') or hasAuthority('system:employee:add') or hasAuthority('system:employee:assignRole')")
    public ApiResult<List<RoleVO>> list(@RequestParam(required = false) String keyword) {
        return ApiResult.success(queries.roles(keyword));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('system:role:list') or hasAuthority('system:role:assign')")
    public ApiResult<List<Permission>> permissions() {
        return ApiResult.success(queries.permissions());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('system:role:add')")
    public ApiResult<Long> create(@Valid @RequestBody RoleRequest request) {
        return ApiResult.success(service.create(request));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('system:role:edit')")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        service.update(id, request);
        return ApiResult.success(null);
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResult.success(null);
    }

    @PostMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('system:role:assign')")
    public ApiResult<Void> permissions(@PathVariable Long id, @Valid @RequestBody RolePermissionsRequest request) {
        service.assignPermissions(id, request.permissionIds());
        return ApiResult.success(null);
    }
}
