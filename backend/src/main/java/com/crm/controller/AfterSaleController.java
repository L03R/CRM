package com.crm.controller;
import com.crm.common.*;
import com.crm.dto.*;
import com.crm.entity.*;
import com.crm.vo.*;
import com.crm.service.AfterSaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.io.IOException;
@RestController @RequestMapping("/api/after-sales") @RequiredArgsConstructor
public class AfterSaleController {
    private final AfterSaleService service;
    @GetMapping @PreAuthorize("hasAuthority('aftersale:list')")
    public ApiResult<PageResult<AfterSaleVO>> page(@AuthenticationPrincipal User user,@Valid AfterSaleQuery query) {return ApiResult.success(service.page(user,query));}
    @GetMapping("/owners") @PreAuthorize("hasAuthority('aftersale:list')")
    public ApiResult<List<Map<String,Object>>> owners(@AuthenticationPrincipal User user) {return ApiResult.success(service.owners(user));}
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('aftersale:list')")
    public ApiResult<AfterSaleVO> detail(@PathVariable Long id,@AuthenticationPrincipal User user) {return ApiResult.success(service.detail(id,user));}
    @GetMapping("/customers") @PreAuthorize("hasAuthority('aftersale:add')")
    public ApiResult<List<ContactCustomerVO>> customers(@AuthenticationPrincipal User user,@RequestParam(required=false) String keyword) {return ApiResult.success(service.customers(user,keyword));}
    @GetMapping("/sources") @PreAuthorize("hasAnyAuthority('aftersale:add','aftersale:source')")
    public ApiResult<Map<String,Object>> sources(@AuthenticationPrincipal User user,@RequestParam Long customerId,@RequestParam(required=false) Long orderId,@RequestParam(required=false) Long shipmentId,@RequestParam(required=false) Long caseId) {return ApiResult.success(service.sources(user,customerId,orderId,shipmentId,caseId));}
    @GetMapping("/{id}/people") @PreAuthorize("hasAnyAuthority('aftersale:assign','aftersale:transfer')")
    public ApiResult<Map<String,List<Map<String,Object>>>> people(@PathVariable Long id,@AuthenticationPrincipal User user) {return ApiResult.success(service.people(id,user));}
    @PostMapping @PreAuthorize("hasAuthority('aftersale:add')")
    public ApiResult<Long> create(@AuthenticationPrincipal User user,@Valid @RequestBody AfterSaleRequest request) {return ApiResult.success(service.create(request,user));}
    @PutMapping("/{id}/source") @PreAuthorize("hasAuthority('aftersale:source')")
    public ApiResult<Void> source(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody AfterSaleSourceUpdate request) {service.source(id,request,user);return ApiResult.success(null);}
    @PostMapping("/{id}/transfer") @PreAuthorize("hasAuthority('aftersale:transfer')")
    public ApiResult<Void> transfer(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody AfterSaleTransferRequest request) {service.transfer(id,request,user);return ApiResult.success(null);}
    @PostMapping("/{id}/follow") @PreAuthorize("hasAuthority('aftersale:follow')")
    public ApiResult<Void> follow(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody AfterSaleFollowRequest request) {service.follow(id,request,user);return ApiResult.success(null);}
    @PostMapping("/{id}/assist") @PreAuthorize("hasAuthority('aftersale:assist')")
    public ApiResult<Void> assist(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody AfterSaleFollowRequest request) {service.assist(id,request,user);return ApiResult.success(null);}
    @PostMapping("/{id}/owner") @PreAuthorize("hasAuthority('aftersale:assign')")
    public ApiResult<Void> assign(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody AfterSaleAssignRequest request) {service.assign(id,request,user);return ApiResult.success(null);}
    @PostMapping("/{id}/close") @PreAuthorize("hasAuthority('aftersale:close')")
    public ApiResult<Void> close(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody AfterSaleCloseRequest request) {service.close(id,request,user);return ApiResult.success(null);}
    @PostMapping("/{id}/reopen") @PreAuthorize("hasAuthority('aftersale:reopen')")
    public ApiResult<Void> reopen(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody AfterSaleReopenRequest request) {service.reopen(id,request,user);return ApiResult.success(null);}
    @PostMapping("/{id}/images") @PreAuthorize("hasAnyAuthority('aftersale:follow','aftersale:assist')")
    public ApiResult<Long> upload(@PathVariable Long id,@AuthenticationPrincipal User user,@RequestParam MultipartFile file) throws IOException {return ApiResult.success(service.upload(id,file,user));}
    @GetMapping("/{caseId}/images/{id}") @PreAuthorize("hasAuthority('aftersale:list')")
    public ResponseEntity<byte[]> image(@PathVariable Long caseId,@PathVariable Long id,@AuthenticationPrincipal User user) {
        var image=service.image(caseId,id,user);return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.getMimeType())).cacheControl(CacheControl.noStore()).body(image.getImageData());
    }
}
