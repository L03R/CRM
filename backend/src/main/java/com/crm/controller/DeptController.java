package com.crm.controller;

import com.crm.common.ApiResult;
import com.crm.dto.DeptRequest;
import com.crm.service.DeptService;
import com.crm.vo.DeptVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;

@RestController
@RequestMapping("/api/depts")
@RequiredArgsConstructor
public class DeptController {
    private final DeptService service;

    @GetMapping
    @PreAuthorize("hasAuthority('system:dept:list')")
    public ApiResult<List<DeptVO>> tree(@RequestParam(required = false) String keyword) {
        return ApiResult.success(service.tree(keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:list')")
    public ApiResult<DeptVO> detail(@PathVariable Long id) {
        return ApiResult.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:dept:add')")
    public ApiResult<Long> create(@Valid @RequestBody DeptRequest request) {
        return ApiResult.success(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:edit')")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody DeptRequest request) {
        service.update(id, request);
        return ApiResult.success(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:delete')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResult.success(null);
    }
}
