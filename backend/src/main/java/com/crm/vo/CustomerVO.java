package com.crm.vo;

import com.crm.entity.Customer;
import lombok.Getter;
import lombok.Setter;
import java.util.*;

@Getter
@Setter
public class CustomerVO extends Customer {
    private Long ownerDeptId;
    private Long pendingRequestId;
    private String ownerName;
    private String creatorName;
    private String poolDeptName;
    private String poolRequestReason;
    private String sourceCreatorName;
    private Integer activeOpportunities;
    private List<String> actions=new ArrayList<>();
}
