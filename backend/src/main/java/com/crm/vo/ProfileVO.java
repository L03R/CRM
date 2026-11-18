package com.crm.vo;

import com.crm.entity.Permission;
import java.util.List;

public record ProfileVO(UserVO user, List<Permission> permissions, List<String> permissionCodes) {}
