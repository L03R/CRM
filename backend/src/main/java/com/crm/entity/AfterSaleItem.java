package com.crm.entity;
import lombok.Data;
import java.time.*;
import java.util.*;
@Data
public class AfterSaleItem {
    private Long id;
    private Long caseId;
    private Long orderItemId;
    private String productName;
    private String skuCode;
    private String specification;
    private Integer quantity;
}
