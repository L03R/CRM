package com.crm.vo;
import com.crm.entity.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.*;
@Data @EqualsAndHashCode(callSuper=true)
public class QuotationVO extends Quotation {
    private String currentCustomerName;
    private String opportunityTitle;
    private String opportunityStage;
    private Long ownerId;
    private Long ownerDeptId;
    private String ownerName;
    private String displayStatus;
    private Integer latestVersionNo;
    private Long orderId;
    private String orderStatus;
    private Long businessOrderQuoteId;
    private List<String> actions=new ArrayList<>();
    private List<QuotationItem> items=List.of();
    private List<QuotationEvent> events=List.of();
    private List<Quotation> versions=List.of();
}
