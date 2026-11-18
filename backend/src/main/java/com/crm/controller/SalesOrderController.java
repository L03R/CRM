package com.crm.controller;
import com.crm.common.*;
import com.crm.dto.*;
import com.crm.entity.User;
import com.crm.service.SalesOrderService;
import com.crm.vo.SalesOrderVO;
import com.crm.vo.QuotationSkuVO;
import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/orders") @RequiredArgsConstructor
public class SalesOrderController {
    private final SalesOrderService service;
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('order:delete')")
    public ApiResult<Void> delete(@PathVariable Long id,@AuthenticationPrincipal User user) {
        service.delete(id,user);
        return ApiResult.success(null);
    }
    @PostMapping("/from-quotation/{id}") @PreAuthorize("hasAuthority('order:add')")
    public ApiResult<Long> generate(@PathVariable Long id,@AuthenticationPrincipal User user){return ApiResult.success(service.generate(id,user));}
    @GetMapping @PreAuthorize("hasAuthority('order:list')")
    public ApiResult<PageResult<SalesOrderVO>> page(@AuthenticationPrincipal User user,@Valid OrderQuery query){return ApiResult.success(service.page(user,query));}
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('order:list')")
    public ApiResult<SalesOrderVO> detail(@PathVariable Long id,@AuthenticationPrincipal User user){return ApiResult.success(service.detail(id,user));}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('order:edit')")
    public ApiResult<Void> update(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody OrderDraftRequest request){service.update(id,request,user);return ApiResult.success(null);}
    @PostMapping("/{id}/confirm") @PreAuthorize("hasAuthority('order:confirm')")
    public ApiResult<Void> confirm(@PathVariable Long id,@AuthenticationPrincipal User user){service.confirm(id,user);return ApiResult.success(null);}
    @PostMapping("/{id}/cancel") @PreAuthorize("hasAuthority('order:cancel')")
    public ApiResult<Void> cancel(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody ReasonRequest request){service.cancel(id,request,user);return ApiResult.success(null);}

    @GetMapping("/{id}/skus") @PreAuthorize("hasAnyAuthority('order:edit','order:change')")
    public ApiResult<List<QuotationSkuVO>> skus(@PathVariable Long id,@AuthenticationPrincipal User user,@RequestParam(required=false) String keyword) {
        return ApiResult.success(service.skus(id,user,keyword));
    }

    @PostMapping("/{id}/change") @PreAuthorize("hasAuthority('order:change')")
    public ApiResult<Void> change(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody OrderChangeRequest request) {
        service.change(id,request,user);
        return ApiResult.success(null);
    }

    @PostMapping("/{id}/cancel-confirmed") @PreAuthorize("hasAuthority('order:cancel:confirmed')")
    public ApiResult<Void> cancelConfirmed(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody OrderCancelRequest request) {
        service.cancelConfirmed(id,request,user);
        return ApiResult.success(null);
    }
}
