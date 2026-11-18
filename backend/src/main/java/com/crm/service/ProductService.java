package com.crm.service;

import com.crm.common.BusinessException;
import com.crm.dto.*;
import com.crm.entity.*;
import com.crm.mapper.ProductMapper;
import com.crm.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import com.crm.common.PageResult;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductMapper mapper;
    private final ProductAttributeService attributes;
    private final ProductAttributeManageService attributeManagement;

    public List<ProductCategoryVO> categories() {
        Map<Long,ProductCategoryVO> nodes = new LinkedHashMap<>();
        mapper.categories().forEach(c -> nodes.put(c.getId(),c));
        List<ProductCategoryVO> roots = new ArrayList<>();
        for (ProductCategoryVO category : nodes.values()) {
            if (category.getParentId()==null) roots.add(category);
            else nodes.get(category.getParentId()).getChildren().add(category);
        }
        return roots;
    }

    @Transactional
    public Long createCategory(ProductCategoryRequest request) {
        mapper.lockCategories();
        validateParent(null,request.parentId());
        ProductCategory category = toCategory(request);
        mapper.insertCategory(category);
        return category.getId();
    }

    @Transactional
    public void updateCategory(Long id,ProductCategoryRequest request) {
        mapper.lockCategories();
        ProductCategory existing = category(id);
        if (!existing.getCategoryCode().equals(request.categoryCode())) throw new BusinessException("分类编码不能修改");
        validateParent(id,request.parentId());
        if (!Objects.equals(existing.getParentId(),request.parentId())) attributeManagement.checkCategoryMove(id);
        ProductCategory category = toCategory(request);
        category.setId(id);
        mapper.updateCategory(category);
        attributeManagement.validateHierarchy(id);
    }

    @Transactional
    public void deleteCategory(Long id) {
        mapper.lockCategories();
        category(id);
        if (mapper.countChildren(id)>0) throw new BusinessException("请先迁移或删除下级分类");
        if (mapper.countProducts(id)>0) throw new BusinessException("分类已有产品，包含下架产品，不能删除");
        mapper.deleteCategory(id);
    }

    public List<ProductVO> list(String keyword,Long categoryId,Integer status,Integer supplyType) {
        List<Long> ids=categoryIds(categoryId);
        List<ProductVO> products=mapper.list(keyword,ids,status,supplyType);
        products.forEach(this::setAvailable);
        return products;
    }

    public PageResult<ProductVO> page(String keyword,Long categoryId,Integer status,Integer supplyType,PageQuery page) {
        List<Long> ids=categoryIds(categoryId);
        List<ProductVO> records=mapper.page(keyword,ids,status,supplyType,page.getOffset(),page.getSize());
        records.forEach(this::setAvailable);
        return new PageResult<>(records,mapper.count(keyword,ids,status,supplyType));
    }

    private List<Long> categoryIds(Long categoryId) {
        if (categoryId==null) return null;
        category(categoryId);
        List<Long> ids=new ArrayList<>();
        collectCategories(categoryId,mapper.categories(),ids);
        return ids;
    }

    public ProductVO detail(Long id) {
        ProductVO product=mapper.detail(id);
        if (product==null) throw new BusinessException("产品不存在");
        product.setSkus(mapper.skus(id));
        product.setAttributeTemplate(attributes.template(product.getCategoryId(),"MODEL"));
        product.setSkuAttributeTemplate(attributes.template(product.getCategoryId(),"SKU"));
        product.setAttributes(attributes.values(id,null,product.getAttributeTemplate()));
        product.setMissingAttributes(attributes.missing(product.getAttributeTemplate(),product.getAttributes()));
        if (product.getAttributeTemplate().stream().anyMatch(f->f.getAttributeCode().equals("top_construction"))) product.setConstructionSummary(attributes.constructionSummary(product.getAttributes()));
        for (ProductSku sku:product.getSkus()) {
            sku.setAttributes(attributes.values(null,sku.getId(),product.getSkuAttributeTemplate()));
            sku.setColor(Objects.toString(sku.getAttributes().get("color"),null));
            sku.setHandStyle(Objects.toString(sku.getAttributes().get("hand_style"),null));
            sku.setPackaging(Objects.toString(sku.getAttributes().get("packaging"),null));
            sku.setMissingAttributes(attributes.missing(product.getSkuAttributeTemplate(),sku.getAttributes()));
        }
        setAvailable(product);
        return product;
    }

    @Transactional
    public Long create(ProductRequest request) {
        mapper.lockCategories();
        validateProductCategory(request.categoryId());
        var fields=attributes.template(request.categoryId(),"MODEL");
        var values=attributes.normalize(fields,request.attributes());
        Product product=toProduct(request);
        mapper.insertProduct(product);
        attributes.saveModel(product.getId(),fields,values);
        return product.getId();
    }

    @Transactional
    public void update(Long id,ProductRequest request) {
        mapper.lockCategories();
        ProductVO existing=detail(id);
        if (!existing.getModelCode().equals(request.modelCode())) throw new BusinessException("型号编码不能修改");
        validateProductCategory(request.categoryId());
        var fields=attributes.template(request.categoryId(),"MODEL");
        var values=attributes.normalize(fields,request.attributes());
        Product product=toProduct(request);
        product.setId(id);
        mapper.updateProduct(product);
        if (!existing.getCategoryId().equals(request.categoryId())) attributes.clearModel(id);
        attributes.saveModel(id,fields,values);
        if (!existing.getCategoryId().equals(request.categoryId())) {
            for (ProductSku sku:existing.getSkus()) attributes.reconcileSku(sku.getId(),request.categoryId());
        }
    }

    @Transactional
    public void status(Long id,Integer status) {
        mapper.lockCategories();
        detail(id);
        mapper.productStatus(id,status);
    }

    @Transactional
    public Long createSku(Long productId,ProductSkuRequest request) {
        mapper.lockCategories();
        ProductVO product=detail(productId);
        ProductSku sku=toSku(request);
        sku.setProductId(productId);
        var fields=attributes.template(product.getCategoryId(),"SKU");
        var values=attributes.normalizeSku(fields,request.attributes(),sku);
        mapper.insertSku(sku);
        attributes.saveSku(sku.getId(),fields,values);
        return sku.getId();
    }

    @Transactional
    public void updateSku(Long productId,Long skuId,ProductSkuRequest request) {
        mapper.lockCategories();
        ProductSku existing=sku(productId,skuId);
        if (!existing.getSkuCode().equals(request.skuCode())) throw new BusinessException("SKU编码不能修改");
        ProductSku sku=toSku(request);
        sku.setId(skuId);
        var fields=attributes.template(detail(productId).getCategoryId(),"SKU");
        var values=attributes.normalizeSku(fields,request.attributes(),sku);
        mapper.updateSku(sku);
        attributes.saveSku(skuId,fields,values);
    }

    @Transactional
    public void skuStatus(Long productId,Long skuId,Integer status) {
        mapper.lockCategories();
        sku(productId,skuId);
        mapper.skuStatus(skuId,status);
    }

    private ProductSku sku(Long productId,Long skuId) {
        ProductSku sku=mapper.sku(skuId);
        if (sku==null || !sku.getProductId().equals(productId)) throw new BusinessException("该产品下的SKU不存在");
        return sku;
    }

    private ProductCategory category(Long id) {
        ProductCategory category=mapper.category(id);
        if (category==null) throw new BusinessException("产品分类不存在");
        return category;
    }

    private void validateParent(Long id,Long parentId) {
        if (parentId!=null && mapper.countProducts(parentId)>0) throw new BusinessException("已有产品的分类不能再添加下级");
        for (Long current=parentId;current!=null;current=category(current).getParentId()) {
            if (current.equals(id)) throw new BusinessException("不能移动到自身或下级分类");
        }
    }

    private void validateProductCategory(Long id) {
        if (mapper.countChildren(id)>0) throw new BusinessException("请选择末级产品分类");
        for (Long current=id;current!=null;) {
            ProductCategory category=category(current);
            if (category.getStatus()!=1) throw new BusinessException("分类及其父级必须启用");
            current=category.getParentId();
        }
    }

    private void setAvailable(ProductVO product) {
        boolean available=product.getStatus()==1;
        for (Long current=product.getCategoryId();current!=null;) {
            ProductCategory category=category(current);
            available &= category.getStatus()==1;
            current=category.getParentId();
        }
        product.setAvailable(available);
    }

    private void collectCategories(Long id,List<ProductCategoryVO> all,List<Long> ids) {
        ids.add(id);
        for (ProductCategoryVO category:all) if (id.equals(category.getParentId())) collectCategories(category.getId(),all,ids);
    }

    private ProductCategory toCategory(ProductCategoryRequest request) {
        ProductCategory category=new ProductCategory();
        category.setParentId(request.parentId()); category.setCategoryName(request.categoryName().trim());
        category.setCategoryCode(request.categoryCode()); category.setSortNo(request.sortNo()); category.setStatus(request.status());
        return category;
    }

    private Product toProduct(ProductRequest request) {
        Product product=new Product();
        product.setCategoryId(request.categoryId()); product.setModelCode(request.modelCode());
        product.setProductName(request.productName().trim()); product.setBrand(request.brand().trim());
        product.setSeries(request.series()); product.setSpecifications(request.specifications()); product.setImageUrl(request.imageUrl());
        product.setSupplyType(request.supplyType()); product.setUnit(request.unit());
        product.setRetailPrice(request.retailPrice()); product.setChannelPrice(request.channelPrice()); product.setLeadDays(request.leadDays());
        return product;
    }

    private ProductSku toSku(ProductSkuRequest request) {
        ProductSku sku=new ProductSku();
        sku.setSkuCode(request.skuCode()); sku.setColor(request.color()); sku.setHandStyle(request.handStyle());
        sku.setPackaging(request.packaging()); sku.setSalePrice(request.salePrice());
        return sku;
    }
}
