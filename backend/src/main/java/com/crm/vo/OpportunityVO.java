package com.crm.vo;

import com.crm.entity.Opportunity;
import lombok.Getter;
import lombok.Setter;
import java.util.*;

@Getter
@Setter
public class OpportunityVO extends Opportunity {
    private Long ownerId;
    private Long ownerDeptId;
    private String customerName;
    private String ownerName;
    private List<String> actions=new ArrayList<>();
}
