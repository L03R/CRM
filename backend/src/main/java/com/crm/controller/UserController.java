package com.crm.controller;

import com.crm.common.ApiResult;
import com.crm.entity.User;
import com.crm.service.DeptService;
import com.crm.service.SystemQueryService;
import com.crm.service.UserService;
import com.crm.vo.CreatedEmployeeVO;
import com.crm.vo.DeptVO;
import com.crm.vo.EmployeeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.crm.dto.*;
import com.crm.vo.EmployeeOptionVO;
import com.crm.common.PageResult;

@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService service;
    private final SystemQueryService queries;
    private final DeptService deptService;

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('system:dept:add') or hasAuthority('system:dept:edit') or hasAuthority('system:employee:list')")
    public ApiResult<List<EmployeeOptionVO>> options() {return ApiResult.success(service.options());}

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:employee:list')")
    public ApiResult<PageResult<EmployeeVO>> page(@RequestParam(required=false) String keyword,@RequestParam(required=false) Long deptId,
                                                 @RequestParam(required=false) Integer status,@Valid PageQuery page) {
        return ApiResult.success(queries.employeePage(keyword,deptId,status,page));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:employee:edit')")
    public ApiResult<Void> update(@PathVariable Long id,@Valid @RequestBody UpdateEmployeeRequest request,@AuthenticationPrincipal User operator) {
        service.update(id,request,operator.getId()); return ApiResult.success(null);
    }

    @PutMapping("/{id}/state")
    @PreAuthorize("hasAuthority('system:employee:status')")
    public ApiResult<Void> state(@PathVariable Long id,@Valid @RequestBody EmployeeStateRequest request,@AuthenticationPrincipal User operator) {
        service.state(id,request,operator.getId()); return ApiResult.success(null);
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('system:employee:resetPwd')")
    public ApiResult<CreatedEmployeeVO> resetPassword(@PathVariable Long id,@AuthenticationPrincipal User operator) {return ApiResult.success(service.resetPassword(id,operator.getId()));}

    @GetMapping
    @PreAuthorize("hasAuthority('system:employee:list')")
    public ApiResult<List<EmployeeVO>> employees(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Long deptId,
                                                @RequestParam(required = false) Integer status) {
        return ApiResult.success(queries.employees(keyword, deptId, status));
    }

    @GetMapping("/departments")
    @PreAuthorize("hasAuthority('system:employee:list') or hasAuthority('system:employee:add') or hasAuthority('system:employee:edit')")
    public ApiResult<List<DeptVO>> departments() {
        return ApiResult.success(deptService.tree(null));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:employee:add') and (#request.roleIds().isEmpty() or hasAuthority('system:employee:assignRole'))")
    public ApiResult<CreatedEmployeeVO> create(@Valid @RequestBody CreateEmployeeRequest request,
                                              @AuthenticationPrincipal User operator) {
        return ApiResult.success(service.create(request, operator.getId()));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('system:employee:assignRole')")
    public ApiResult<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesRequest request,
                                       @AuthenticationPrincipal User operator) {
        service.assignRoles(id, request.roleIds(), operator.getId());
        return ApiResult.success(null);
    }
}
