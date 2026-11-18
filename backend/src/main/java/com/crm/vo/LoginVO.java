package com.crm.vo;

import com.crm.entity.Permission;
import java.time.LocalDateTime;
import java.util.List;

public record LoginVO(String token, LocalDateTime expiresAt, UserVO user,
                      List<Permission> permissions, List<String> permissionCodes) {}
