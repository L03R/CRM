package com.crm.vo;

import com.crm.entity.Permission;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class MenuVO extends Permission {
    private List<MenuVO> children = new ArrayList<>();
}
