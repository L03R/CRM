package com.crm.controller;

import com.crm.common.ApiResult;
import com.crm.dto.ProductAttributeRequest;
import com.crm.service.ProductAttributeManageService;
import com.crm.vo.ProductAttributeTemplateVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products/categories/{categoryId}/attribute-definitions")
@RequiredArgsConstructor
public class ProductAttributeController {
    private final ProductAttributeManageService service;

    @GetMapping
    @PreAuthorize("hasAuthority('product:category:list')")
    public ApiResult<ProductAttributeTemplateVO> list(@PathVariable Long categoryId) {return ApiResult.success(service.list(categoryId));}

    @PostMapping
    @PreAuthorize("hasAuthority('product:category:edit')")
    public ApiResult<Long> create(@PathVariable Long categoryId,@Valid @RequestBody ProductAttributeRequest request) {return ApiResult.success(service.create(categoryId,request));}

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('product:category:edit')")
    public ApiResult<Void> update(@PathVariable Long categoryId,@PathVariable Long id,@Valid @RequestBody ProductAttributeRequest request) {service.update(categoryId,id,request);return ApiResult.success(null);}

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('product:category:edit')")
    public ApiResult<Void> delete(@PathVariable Long categoryId,@PathVariable Long id) {service.delete(categoryId,id);return ApiResult.success(null);}
}
