package com.crm.config;

import com.crm.entity.User;
import com.crm.mapper.AuthMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "crm.bootstrap.enabled", havingValue = "true")
public class AdminInitializer implements ApplicationRunner {
    private final AuthMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    @Value("${crm.bootstrap.admin-password}")
    private String initialPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (mapper.findByEmployeeNo("ADMIN001") != null) return;
        User admin = new User();
        admin.setEmployeeNo("ADMIN001");
        admin.setRealName("系统管理员");
        admin.setDeptId(jdbc.queryForObject("SELECT id FROM sys_dept WHERE dept_code='DEPT_HQ' AND deleted=0", Long.class));
        admin.setPasswordHash(passwordEncoder.encode(initialPassword));
        mapper.insertUser(admin);
        if (mapper.assignAdminRole(admin.getId()) == 0) {
            throw new IllegalStateException("请先执行 crm-rbac-seed.sql 初始化管理员角色");
        }
    }
}
