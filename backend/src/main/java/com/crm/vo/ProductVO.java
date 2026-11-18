package com.crm.vo;

import com.crm.entity.Product;
import com.crm.entity.ProductSku;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ProductVO extends Product {
    private String categoryName;
    private Integer skuCount;
    private boolean available;
    private List<ProductSku> skus = new ArrayList<>();
    private List<ProductAttributeVO> attributeTemplate = List.of();
    private List<ProductAttributeVO> skuAttributeTemplate = List.of();
    private Map<String,Object> attributes = Map.of();
    private List<String> missingAttributes = List.of();
    private String constructionSummary;
}
