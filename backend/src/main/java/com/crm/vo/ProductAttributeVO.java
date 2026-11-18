package com.crm.vo;

import com.crm.entity.ProductAttribute;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class ProductAttributeVO extends ProductAttribute {
    private List<String> options = List.of();
    private String sourceCategoryName;
    private boolean inherited;
    private boolean used;
}
