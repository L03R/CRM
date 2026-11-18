package com.crm.controller;

import com.crm.common.ApiResult;
import com.crm.dto.LoginRequest;
import com.crm.dto.ChangePasswordRequest;
import com.crm.entity.User;
import com.crm.service.AuthService;
import com.crm.service.PermissionService;
import com.crm.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final PermissionService permissionService;

    @PutMapping("/password")
    public ApiResult<Void> changePassword(@AuthenticationPrincipal User user,@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.getId(),request); return ApiResult.success(null);
    }

    @PostMapping("/login")
    public ApiResult<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return ApiResult.success(authService.login(request));
    }

    @GetMapping({"/me", "/profile"})
    public ApiResult<ProfileVO> profile(@AuthenticationPrincipal User user) {
        return ApiResult.success(authService.profile(user));
    }

    @GetMapping("/menus")
    public ApiResult<List<MenuVO>> menus(@AuthenticationPrincipal User user) {
        return ApiResult.success(permissionService.menus(user.getId()));
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(@RequestHeader("Authorization") String header) {
        authService.logout(header.substring(7));
        return ApiResult.success(null);
    }
}
