package com.crm.controller;
import com.crm.common.*;
import com.crm.dto.*;
import com.crm.entity.User;
import com.crm.service.ContactService;
import com.crm.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {
    private final ContactService service;
    @GetMapping @PreAuthorize("hasAuthority('contact:list')")
    public ApiResult<PageResult<ContactVO>> page(@AuthenticationPrincipal User user,@RequestParam(required=false) Long customerId,
            @RequestParam(required=false) String keyword,@RequestParam(required=false) Integer status,@Valid PageQuery page) {
        return ApiResult.success(service.page(user,customerId,keyword,status,page));
    }
    @GetMapping("/customers") @PreAuthorize("hasAuthority('contact:add')")
    public ApiResult<List<ContactCustomerVO>> customers(@AuthenticationPrincipal User user,@RequestParam(required=false) String keyword) {
        return ApiResult.success(service.customers(user,keyword));
    }
    @PostMapping @PreAuthorize("hasAuthority('contact:add')")
    public ApiResult<Long> create(@AuthenticationPrincipal User user,@Valid @RequestBody ContactRequest request) {
        return ApiResult.success(service.save(null,request,user));
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('contact:edit')")
    public ApiResult<Long> update(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody ContactRequest request) {
        return ApiResult.success(service.save(id,request,user));
    }
    @PostMapping("/{id}/primary") @PreAuthorize("hasAuthority('contact:primary')")
    public ApiResult<Void> primary(@PathVariable Long id,@AuthenticationPrincipal User user) {service.primary(id,user);return ApiResult.success(null);}
    @PutMapping("/{id}/status") @PreAuthorize("hasAuthority('contact:status')")
    public ApiResult<Void> status(@PathVariable Long id,@RequestParam int status,@AuthenticationPrincipal User user) {service.status(id,status,user);return ApiResult.success(null);}
}
