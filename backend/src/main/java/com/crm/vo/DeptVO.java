package com.crm.vo;

import com.crm.entity.Dept;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class DeptVO extends Dept {
    private String leaderName;
    private int memberCount;
    private List<DeptVO> children = new ArrayList<>();
}
