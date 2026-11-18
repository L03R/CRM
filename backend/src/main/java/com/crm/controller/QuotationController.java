package com.crm.controller;
import com.crm.common.*;
import com.crm.dto.*;
import com.crm.entity.User;
import com.crm.service.QuotationService;
import com.crm.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/quotations") @RequiredArgsConstructor
public class QuotationController {
    private final QuotationService service;
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('quotation:delete')")
    public ApiResult<Void> delete(@PathVariable Long id,@AuthenticationPrincipal User user) {
        service.delete(id,user);
        return ApiResult.success(null);
    }
    @GetMapping @PreAuthorize("hasAuthority('quotation:list')")
    public ApiResult<PageResult<QuotationVO>> page(@AuthenticationPrincipal User user,@RequestParam(required=false) Long customerId,
            @RequestParam(required=false) Long opportunityId,@RequestParam(required=false) String keyword,
            @RequestParam(required=false) String status,@RequestParam(defaultValue="false") boolean onlyFinal,@Valid PageQuery page) {
        return ApiResult.success(service.page(user,customerId,opportunityId,keyword,status,onlyFinal,page));
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('quotation:list')")
    public ApiResult<QuotationVO> detail(@PathVariable Long id,@AuthenticationPrincipal User user) {return ApiResult.success(service.detail(id,user));}
    @GetMapping("/customers") @PreAuthorize("hasAuthority('quotation:add')")
    public ApiResult<List<ContactCustomerVO>> customers(@AuthenticationPrincipal User user,@RequestParam(required=false) String keyword) {return ApiResult.success(service.customers(user,keyword));}
    @GetMapping("/opportunities") @PreAuthorize("hasAuthority('quotation:add')")
    public ApiResult<List<OpportunityVO>> opportunities(@AuthenticationPrincipal User user,@RequestParam(required=false) Long customerId,@RequestParam(required=false) String keyword) {return ApiResult.success(service.opportunities(user,customerId,keyword));}
    @GetMapping("/contacts") @PreAuthorize("hasAuthority('quotation:add') or hasAuthority('quotation:edit')")
    public ApiResult<List<ContactVO>> contacts(@AuthenticationPrincipal User user,@RequestParam Long customerId,@RequestParam(required=false) String keyword,@RequestParam(defaultValue="CREATE") String mode) {return ApiResult.success(service.contacts(user,customerId,keyword,mode));}
    @GetMapping("/skus") @PreAuthorize("hasAuthority('product:list') and (hasAuthority('quotation:add') or hasAuthority('quotation:edit'))")
    public ApiResult<List<QuotationSkuVO>> skus(@AuthenticationPrincipal User user,@RequestParam(required=false) String keyword) {return ApiResult.success(service.skus(user,keyword));}
    @PostMapping @PreAuthorize("hasAuthority('quotation:add')")
    public ApiResult<Long> create(@AuthenticationPrincipal User user,@Valid @RequestBody QuotationRequest request) {return ApiResult.success(service.create(request,user));}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('quotation:edit')")
    public ApiResult<Void> update(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody QuotationRequest request) {service.update(id,request,user);return ApiResult.success(null);}
    @PostMapping("/{id}/revision") @PreAuthorize("hasAuthority('quotation:revise')")
    public ApiResult<Long> revise(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody ReasonRequest request) {return ApiResult.success(service.revise(id,request,user));}
    @PostMapping("/{id}/transition") @PreAuthorize("hasAnyAuthority('quotation:send','quotation:accept','quotation:final','quotation:void')")
    public ApiResult<Void> transition(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody QuotationActionRequest request) {service.transition(id,request,user);return ApiResult.success(null);}
}
