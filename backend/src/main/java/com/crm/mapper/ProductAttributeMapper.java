package com.crm.mapper;

import com.crm.entity.ProductAttribute;
import com.crm.entity.ProductAttributeValue;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface ProductAttributeMapper {
    List<ProductAttribute> definitions(Long categoryId);
    ProductAttribute definition(Long id);
    void insertDefinition(ProductAttribute attribute);
    void updateDefinition(ProductAttribute attribute);
    void deleteDefinition(Long id);
    int countReferences(Long id);
    int countUsage(@Param("categoryIds") List<Long> categoryIds,@Param("scope") String scope,@Param("code") String code);
    List<ProductAttributeValue> values(@Param("productId") Long productId,@Param("skuId") Long skuId);
    void deleteValues(@Param("productId") Long productId,@Param("skuId") Long skuId);
    void deleteEditableValues(@Param("productId") Long productId,@Param("skuId") Long skuId,@Param("codes") List<String> codes);
    void insertValues(@Param("productId") Long productId,@Param("skuId") Long skuId,@Param("values") List<ProductAttributeValue> values);
}
