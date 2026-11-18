package com.crm.controller;

import com.crm.security.TokenUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    private String employeeNo;
    private long userId;
    private long roleId;

    @BeforeEach
    void prepareEmployee() {
        employeeNo = "T" + UUID.randomUUID().toString().substring(0, 20);
        long deptId = jdbc.queryForObject("SELECT id FROM sys_dept WHERE dept_code='DEPT_HQ'", Long.class);
        jdbc.update("INSERT INTO sys_user(employee_no,real_name,password_hash,dept_id) VALUES(?,?,?,?)",
                employeeNo, "认证测试员工", encoder.encode("Test@123456"), deptId);
        userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?", Long.class, employeeNo);
        jdbc.update("INSERT INTO sys_role(role_code,role_name) VALUES(?,?)", employeeNo, "认证测试角色");
        roleId = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code=?", Long.class, employeeNo);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", userId, roleId);
    }

    @Test
    void loginProfileAndLogout() throws Exception {
        grant("system:dept:list");
        JsonNode login = login();
        String token = login.path("token").asText();
        assertFalse(token.isBlank());
        assertFalse(login.toString().contains("passwordHash"));
        assertNotNull(login.get("expiresAt"));
        assertEquals(TokenUtils.hash(token), jdbc.queryForObject(
                "SELECT token_hash FROM sys_login_session WHERE user_id=?", String.class, userId));
        assertNotNull(jdbc.queryForObject("SELECT last_login_at FROM sys_user WHERE id=?", Object.class, userId));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.user.employeeNo").value(employeeNo));
        mvc.perform(get("/api/auth/profile").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/menus").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].permissionCode").value("menu:system"))
                .andExpect(jsonPath("$.data[0].children[0].permissionCode").value("menu:system:dept"));
        mvc.perform(get("/api/depts").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongCredentialsAndUnavailableAccounts() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("employeeNo", employeeNo, "password", "wrong"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        for (String column : new String[]{"status", "employment_status", "deleted"}) {
            int unavailable = column.equals("deleted") ? 1 : 0;
            jdbc.update("UPDATE sys_user SET " + column + "=? WHERE id=?", unavailable, userId);
            mvc.perform(post("/api/auth/login").contentType("application/json")
                    .content(credentials())).andExpect(status().isUnauthorized());
            jdbc.update("UPDATE sys_user SET " + column + "=? WHERE id=?", 1 - unavailable, userId);
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_login_session WHERE user_id=?", Integer.class, userId));
    }

    @Test
    void rejectsMissingTamperedAndExpiredTokens() throws Exception {
        mvc.perform(get("/api/depts")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        String token = login().path("token").asText();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());
        jdbc.update("UPDATE sys_login_session SET expires_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE user_id=?", userId);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void menuGrantDoesNotGrantOperationsAndRoleChangesApplyImmediately() throws Exception {
        grant("menu:system:dept");
        String token = login().path("token").asText();
        mvc.perform(get("/api/depts").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        grant("system:dept:list");
        mvc.perform(get("/api/depts").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(delete("/api/depts/9999999").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        jdbc.update("UPDATE sys_role SET status=0 WHERE id=?", roleId);
        mvc.perform(get("/api/depts").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/menus").header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.data").isEmpty());
        jdbc.update("UPDATE sys_role SET status=1 WHERE id=?", roleId);
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=?", roleId);
        mvc.perform(get("/api/depts").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @Test
    void disablingEmployeeInvalidatesExistingToken() throws Exception {
        String token = login().path("token").asText();
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=?", userId);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void multipleRolesMergePermissionsAndDisabledAncestorsBlockTheirBranch() throws Exception {
        grant("system:dept:list");
        String secondCode = employeeNo + "_2";
        jdbc.update("INSERT INTO sys_role(role_code,role_name) VALUES(?,?)", secondCode, "第二角色");
        long secondRole = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code=?", Long.class, secondCode);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", userId, secondRole);
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT ?,id FROM sys_permission WHERE permission_code IN ('system:dept:list','customer:list')", secondRole);
        JsonNode login = login();
        assertEquals(2, login.path("user").path("roles").size());
        assertEquals(2, login.path("permissionCodes").size());
        String token = login.path("token").asText();
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code='menu:system'");
        mvc.perform(get("/api/depts").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/menus").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].permissionCode").value("menu:customer"));
    }

    private void grant(String permissionCode) {
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT ?,id FROM sys_permission WHERE permission_code=?", roleId, permissionCode);
    }

    private String credentials() throws Exception {
        return json.writeValueAsString(Map.of("employeeNo", employeeNo, "password", "Test@123456"));
    }

    private JsonNode login() throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType("application/json").content(credentials()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return json.readTree(response).get("data");
    }
}
