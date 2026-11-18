package com.crm.vo;

import com.crm.entity.Permission;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PermissionTreeVO extends Permission {
    private boolean protectedNode;
    private List<PermissionTreeVO> children = new ArrayList<>();
}
