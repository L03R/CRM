package com.crm.vo;

import com.crm.entity.ProductCategory;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ProductCategoryVO extends ProductCategory {
    private List<ProductCategoryVO> children = new ArrayList<>();
}
