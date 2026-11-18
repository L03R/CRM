package com.crm.mapper;

import com.crm.entity.*;
import com.crm.vo.*;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface ProductMapper {
    List<Long> lockCategories();
    List<ProductCategoryVO> categories();
    ProductCategory category(Long id);
    void insertCategory(ProductCategory category);
    void updateCategory(ProductCategory category);
    int countChildren(Long id);
    int countProducts(Long categoryId);
    void deleteCategory(Long id);
    List<ProductVO> list(@Param("keyword") String keyword,@Param("categoryIds") List<Long> categoryIds,
                         @Param("status") Integer status,@Param("supplyType") Integer supplyType);
    List<ProductVO> page(@Param("keyword") String keyword,@Param("categoryIds") List<Long> categoryIds,@Param("status") Integer status,@Param("supplyType") Integer supplyType,@Param("offset") int offset,@Param("size") int size);
    long count(@Param("keyword") String keyword,@Param("categoryIds") List<Long> categoryIds,@Param("status") Integer status,@Param("supplyType") Integer supplyType);
    ProductVO detail(Long id);
    void insertProduct(Product product);
    void updateProduct(Product product);
    void productStatus(@Param("id") Long id,@Param("status") Integer status);
    List<ProductSku> skus(Long productId);
    ProductSku sku(Long id);
    void insertSku(ProductSku sku);
    void updateSku(ProductSku sku);
    void skuStatus(@Param("id") Long id,@Param("status") Integer status);
}
