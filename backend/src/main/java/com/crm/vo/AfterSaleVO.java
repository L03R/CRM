package com.crm.vo;
import com.crm.entity.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.*;
@Data @EqualsAndHashCode(callSuper=true)
public class AfterSaleVO extends AfterSale {
    private Long customerOwnerId,customerOwnerDeptId;
    private String customerName,ownerName,helperName,creatorName,orderNo,shipmentNo;
    private boolean sourcePending;
    private List<AfterSaleItem> items=List.of();
    private List<AfterSaleEvent> events=List.of();
    private List<AfterSaleImage> images=List.of();
    private List<String> actions=new ArrayList<>();
}
