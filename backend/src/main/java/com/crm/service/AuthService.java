package com.crm.service;

import com.crm.dto.LoginRequest;
import com.crm.entity.Permission;
import com.crm.entity.User;
import com.crm.mapper.AuthMapper;
import com.crm.security.TokenUtils;
import com.crm.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import com.crm.mapper.UserMapper;
import com.crm.dto.ChangePasswordRequest;
import com.crm.common.BusinessException;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;
    private final UserMapper users;

    @Transactional
    public void changePassword(Long userId,ChangePasswordRequest request) {
        User user=users.lockUser(userId);
        if (user==null || !user.canLogin() || !passwordEncoder.matches(request.oldPassword(),user.getPasswordHash())) throw new BusinessException("原密码不正确");
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length>72) throw new BusinessException("新密码不能超过72字节");
        if (passwordEncoder.matches(request.newPassword(),user.getPasswordHash())) throw new BusinessException("新密码不能与原密码相同");
        users.updatePassword(userId,passwordEncoder.encode(request.newPassword()));
        mapper.revokeUserSessions(userId);
    }
    @Value("${crm.auth.session-hours}")
    private int sessionHours;

    @Transactional
    public LoginVO login(LoginRequest request) {
        User user = mapper.findByEmployeeNo(request.employeeNo().trim());
        if (user == null || !user.canLogin() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("员工编号或密码错误，或账号不可用");
        }
        String token = TokenUtils.create();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(sessionHours);
        mapper.insertSession(user.getId(), TokenUtils.hash(token), expiresAt);
        mapper.updateLastLogin(user.getId());
        ProfileVO profile = profile(user);
        return new LoginVO(token, expiresAt, profile.user(), profile.permissions(), profile.permissionCodes());
    }

    public ProfileVO profile(User user) {
        UserVO info = new UserVO(user.getId(), user.getEmployeeNo(), user.getRealName(), user.getDeptId(),
                mapper.findDeptName(user.getDeptId()), mapper.findRoles(user.getId()));
        List<Permission> permissions = permissionService.permissions(user.getId());
        return new ProfileVO(info, permissions, permissionService.codes(permissions));
    }

    public void logout(String token) {
        mapper.revokeSession(TokenUtils.hash(token));
    }
}
