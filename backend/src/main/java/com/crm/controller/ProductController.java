package com.crm.controller;

import com.crm.common.ApiResult;
import com.crm.dto.*;
import com.crm.service.ProductService;
import com.crm.service.ProductAttributeService;
import com.crm.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.crm.common.PageResult;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService service;
    private final ProductAttributeService attributes;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('product:list')")
    public ApiResult<PageResult<ProductVO>> page(@RequestParam(required=false) String keyword,@RequestParam(required=false) Long categoryId,
                                               @RequestParam(required=false) Integer status,@RequestParam(required=false) Integer supplyType,@Valid PageQuery page) {
        return ApiResult.success(service.page(keyword,categoryId,status,supplyType,page));
    }

    @GetMapping("/categories/{id}/attributes")
    @PreAuthorize("hasAuthority('product:list') or hasAuthority('product:category:list')")
    public ApiResult<ProductAttributeTemplateVO> attributes(@PathVariable Long id) {
        return ApiResult.success(new ProductAttributeTemplateVO(attributes.template(id,"MODEL"),attributes.template(id,"SKU")));
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('product:list') or hasAuthority('product:category:list')")
    public ApiResult<List<ProductCategoryVO>> categories(){return ApiResult.success(service.categories());}

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('product:category:add')")
    public ApiResult<Long> createCategory(@Valid @RequestBody ProductCategoryRequest request){return ApiResult.success(service.createCategory(request));}

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('product:category:edit')")
    public ApiResult<Void> updateCategory(@PathVariable Long id,@Valid @RequestBody ProductCategoryRequest request){service.updateCategory(id,request);return ApiResult.success(null);}

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('product:category:delete')")
    public ApiResult<Void> deleteCategory(@PathVariable Long id){service.deleteCategory(id);return ApiResult.success(null);}

    @GetMapping
    @PreAuthorize("hasAuthority('product:list')")
    public ApiResult<List<ProductVO>> list(@RequestParam(required=false) String keyword,@RequestParam(required=false) Long categoryId,
                                         @RequestParam(required=false) Integer status,@RequestParam(required=false) Integer supplyType){
        return ApiResult.success(service.list(keyword,categoryId,status,supplyType));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('product:list')")
    public ApiResult<ProductVO> detail(@PathVariable Long id){return ApiResult.success(service.detail(id));}

    @PostMapping
    @PreAuthorize("hasAuthority('product:add')")
    public ApiResult<Long> create(@Valid @RequestBody ProductRequest request){return ApiResult.success(service.create(request));}

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('product:edit')")
    public ApiResult<Void> update(@PathVariable Long id,@Valid @RequestBody ProductRequest request){service.update(id,request);return ApiResult.success(null);}

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('product:status')")
    public ApiResult<Void> status(@PathVariable Long id,@Valid @RequestBody ProductStatusRequest request){service.status(id,request.status());return ApiResult.success(null);}

    @PostMapping("/{id}/skus")
    @PreAuthorize("hasAuthority('product:add')")
    public ApiResult<Long> createSku(@PathVariable Long id,@Valid @RequestBody ProductSkuRequest request){return ApiResult.success(service.createSku(id,request));}

    @PutMapping("/{id}/skus/{skuId}")
    @PreAuthorize("hasAuthority('product:edit')")
    public ApiResult<Void> updateSku(@PathVariable Long id,@PathVariable Long skuId,@Valid @RequestBody ProductSkuRequest request){service.updateSku(id,skuId,request);return ApiResult.success(null);}

    @PutMapping("/{id}/skus/{skuId}/status")
    @PreAuthorize("hasAuthority('product:status')")
    public ApiResult<Void> skuStatus(@PathVariable Long id,@PathVariable Long skuId,@Valid @RequestBody ProductStatusRequest request){service.skuStatus(id,skuId,request.status());return ApiResult.success(null);}
}
