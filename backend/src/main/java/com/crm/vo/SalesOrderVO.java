package com.crm.vo;
import com.crm.entity.SalesOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.*;
@Data @EqualsAndHashCode(callSuper=true)
public class SalesOrderVO extends SalesOrder {
    private String customerName;
    private String deliveryStatus;
    private boolean executionStarted;
    private String opportunityTitle;
    private String quoteNo;
    private Integer versionNo;
    private Long ownerId;
    private Long ownerDeptId;
    private String ownerName;
    private OrderSnapshot sourceSnapshot;
    private List<com.crm.entity.SalesOrderItem> items=List.of();
    private List<OrderEventVO> events=List.of();
    private List<String> actions=new ArrayList<>();
}
