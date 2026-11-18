package com.crm.vo;

import com.crm.entity.FollowRecord;
import lombok.Getter;
import lombok.Setter;
import java.util.*;

@Getter
@Setter
public class FollowVO extends FollowRecord {
    private String companyName;
    private String authorName;
    private String resourceType;
}
