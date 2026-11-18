package com.crm.controller;

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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    private long operatorId;
    private long deptId;
    private long adminRoleId;
    private String operatorToken;

    @BeforeEach
    void prepareOperator() throws Exception {
        deptId = jdbc.queryForObject("SELECT id FROM sys_dept WHERE dept_code='DEPT_HQ'", Long.class);
        adminRoleId = roleId("SUPER_ADMIN");
        String employeeNo = number();
        jdbc.update("INSERT INTO sys_user(employee_no,real_name,password_hash,dept_id) VALUES(?,?,?,?)",
                employeeNo, "测试管理人员", encoder.encode("Test@123456"), deptId);
        operatorId = jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?", Long.class, employeeNo);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", operatorId, adminRoleId);
        operatorToken = login(employeeNo, "Test@123456").get("token").asText();
    }

    @Test
    void createsEmployeeAndNewCredentialsReallyLogin() throws Exception {
        String no = number();
        JsonNode created = create(no, List.of(roleId("MARKETING")));
        String password = created.get("initialPassword").asText();
        String hash = jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE employee_no=?", String.class, no);
        assertTrue(encoder.matches(password, hash));
        assertNotEquals(password, hash);
        JsonNode login = login(no, password);
        assertEquals("MARKETING", login.path("user").path("roles").get(0).path("roleCode").asText());
        assertEquals(deptId, login.path("user").path("deptId").asLong());
        mvc.perform(get("/api/auth/menus").header("Authorization", "Bearer " + login.path("token").asText()))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].permissionCode").value("menu:workbench"));
        mvc.perform(post("/api/system/users").header("Authorization", "Bearer " + operatorToken)
                        .contentType("application/json").content(body(no, List.of())))
                .andExpect(status().isConflict());
    }

    @Test
    void reassignmentImmediatelyChangesMenusAndDeduplicatesRoles() throws Exception {
        String no = number();
        JsonNode created = create(no, List.of(roleId("MARKETING")));
        long id = created.path("id").asLong();
        String token = login(no, created.path("initialPassword").asText()).path("token").asText();
        long serviceRole = roleId("CUSTOMER_SERVICE");
        mvc.perform(put("/api/system/users/{id}/roles", id).header("Authorization", "Bearer " + operatorToken)
                        .contentType("application/json").content(json.writeValueAsString(Map.of("roleIds", List.of(serviceRole, serviceRole)))))
                .andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE user_id=?", Integer.class, id));
        mvc.perform(get("/api/auth/menus").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[0].permissionCode").value("menu:workbench"));
        mvc.perform(put("/api/system/users/{id}/roles", id).header("Authorization", "Bearer " + operatorToken)
                        .contentType("application/json").content("{\"roleIds\":[]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/menus").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void invalidDepartmentOrRoleDoesNotCreateEmployee() throws Exception {
        String no = number();
        mvc.perform(post("/api/system/users").header("Authorization", "Bearer " + operatorToken)
                .contentType("application/json").content(body(no, List.of(Long.MAX_VALUE))))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE employee_no=?", Integer.class, no));
        String invalidDept = json.writeValueAsString(Map.of("employeeNo", no, "realName", "测试员工", "deptId", Long.MAX_VALUE, "roleIds", List.of()));
        mvc.perform(post("/api/system/users").header("Authorization", "Bearer " + operatorToken)
                .contentType("application/json").content(invalidDept)).andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE employee_no=?", Integer.class, no));
    }

    @Test
    void cannotRemoveLastEffectiveAdministrator() throws Exception {
        jdbc.update("DELETE FROM sys_user_role WHERE role_id=? AND user_id != ?", adminRoleId, operatorId);
        mvc.perform(put("/api/system/users/{id}/roles", operatorId)
                        .header("Authorization", "Bearer " + operatorToken).contentType("application/json")
                        .content("{\"roleIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("至少保留一名有效的系统管理员"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE user_id=? AND role_id=?", Integer.class, operatorId, adminRoleId));
    }

    @Test
    void nonAdministratorCannotGrantAdminAndCreateRequiresSeparateAssignmentPermission() throws Exception {
        String roleCode = number();
        jdbc.update("INSERT INTO sys_role(role_name,role_code) VALUES('测试员工维护',?)", roleCode);
        long roleId = roleId(roleCode);
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT ?,id FROM sys_permission WHERE permission_code='system:employee:add'", roleId);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", operatorId);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", operatorId, roleId);
        mvc.perform(post("/api/system/users").header("Authorization", "Bearer " + operatorToken)
                .contentType("application/json").content(body(number(), List.of(roleId("MARKETING")))))
                .andExpect(status().isForbidden());
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT ?,id FROM sys_permission WHERE permission_code='system:employee:assignRole'", roleId);
        mvc.perform(post("/api/system/users").header("Authorization", "Bearer " + operatorToken)
                .contentType("application/json").content(body(number(), List.of(adminRoleId))))
                .andExpect(status().isForbidden());
        create(number(), List.of(roleId("MARKETING")));
    }

    private JsonNode create(String no, List<Long> roles) throws Exception {
        String response = mvc.perform(post("/api/system/users").header("Authorization", "Bearer " + operatorToken)
                        .contentType("application/json").content(body(no, roles)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(response).get("data");
    }

    private String body(String no, List<Long> roles) throws Exception {
        return json.writeValueAsString(Map.of("employeeNo", no, "realName", "新增测试员工", "deptId", deptId,
                "phone", "13800000000", "email", "test@example.com", "roleIds", roles));
    }

    private JsonNode login(String no, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("employeeNo", no, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(response).get("data");
    }

    private long roleId(String code) {
        return jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code=?", Long.class, code);
    }

    private String number() { return "T" + UUID.randomUUID().toString().substring(0, 20); }
}
