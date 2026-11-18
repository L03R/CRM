package com.crm.vo;

import com.crm.entity.Lead;
import lombok.Getter;
import lombok.Setter;
import java.util.*;

@Getter
@Setter
public class LeadVO extends Lead {
    private Long ownerDeptId;
    private Long creatorDeptId;
    private Long convertedCustomerId;
    private String ownerName;
    private String creatorName;
    private String targetDeptName;
    private List<String> actions=new ArrayList<>();
}
