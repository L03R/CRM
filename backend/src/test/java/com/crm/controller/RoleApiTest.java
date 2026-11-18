package com.crm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
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
@WithMockUser(authorities = {"system:role:list", "system:role:add", "system:role:edit", "system:role:delete", "system:role:assign"})
class RoleApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    @Test
    void createUpdateDeleteAndReserveCode() throws Exception {
        String code = code();
        long id = create(code);
        assertEquals(0, grantCount(id));
        mvc.perform(put("/api/system/roles/{id}", id).contentType("application/json").content(body(code, 0)))
                .andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("SELECT status FROM sys_role WHERE id=?", Integer.class, id));
        mvc.perform(put("/api/system/roles/{id}", id).contentType("application/json").content(body(code(), 1)))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/system/roles/{id}", id)).andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM sys_role WHERE id=?", Integer.class, id));
        mvc.perform(post("/api/system/roles").contentType("application/json").content(body(code, 1)))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/system/roles/{id}", id)).andExpect(status().isBadRequest());
    }

    @Test
    void authorizationAddsParentsAndReadPermissionAndRejectsInvalidGrantsAtomically() throws Exception {
        long id = create(code());
        long add = permissionId("system:dept:add");
        assign(id, List.of(add, add), 200);
        assertEquals(4, grantCount(id));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id WHERE rp.role_id=? AND p.permission_code='system:dept:list'", Integer.class, id));
        assign(id, List.of(Long.MAX_VALUE), 400);
        assertEquals(4, grantCount(id));
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code='menu:system'");
        assign(id, List.of(add), 400);
        assertEquals(4, grantCount(id));
        assign(id, List.of(), 200);
        assertEquals(0, grantCount(id));
    }

    @Test
    void menuAloneDoesNotGrantActionsAndDeleteClearsAuthorization() throws Exception {
        long id = create(code());
        assign(id, List.of(permissionId("menu:system:dept")), 200);
        assertEquals(2, grantCount(id));
        mvc.perform(delete("/api/system/roles/{id}", id)).andExpect(status().isOk());
        assertEquals(0, grantCount(id));
    }

    @Test
    void builtinRoleCannotBeModifiedOrDeletedOrReauthorized() throws Exception {
        long id = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='SUPER_ADMIN'", Long.class);
        mvc.perform(put("/api/system/roles/{id}", id).contentType("application/json").content(body("SUPER_ADMIN", 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/system/roles/{id}", id)).andExpect(status().isBadRequest());
        assign(id, List.of(), 400);
    }

    @Test
    void assignedRoleCannotBeDeletedEvenForDepartedEmployee() throws Exception {
        long id = create(code());
        String employee = code();
        jdbc.update("INSERT INTO sys_user(employee_no,real_name,password_hash,dept_id,employment_status) SELECT ?, '角色测试员工', ?, id, 0 FROM sys_dept WHERE dept_code='DEPT_HQ'", employee, encoder.encode("Test@123456"));
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT id,? FROM sys_user WHERE employee_no=?", id, employee);
        mvc.perform(delete("/api/system/roles/{id}", id)).andExpect(status().isBadRequest());
    }

    @Test
    void changedGrantsAndDisabledRoleApplyToExistingLogin() throws Exception {
        var manager = SecurityContextHolder.getContext().getAuthentication();
        String code = code();
        long id = create(code);
        String employee = code();
        jdbc.update("INSERT INTO sys_user(employee_no,real_name,password_hash,dept_id) SELECT ?, '权限测试员工', ?, id FROM sys_dept WHERE dept_code='DEPT_HQ'", employee, encoder.encode("Test@123456"));
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT id,? FROM sys_user WHERE employee_no=?", id, employee);
        assign(id, List.of(permissionId("clue:list")), 200);
        String login = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("employeeNo", employee, "password", "Test@123456"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String token = json.readTree(login).path("data").path("token").asText();
        mvc.perform(get("/api/auth/menus").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data[0].permissionCode").value("menu:clue"));
        TestSecurityContextHolder.setAuthentication(manager);
        assign(id, List.of(permissionId("customer:list")), 200);
        mvc.perform(get("/api/auth/menus").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data[0].permissionCode").value("menu:customer"));
        TestSecurityContextHolder.setAuthentication(manager);
        mvc.perform(put("/api/system/roles/{id}", id).contentType("application/json").content(body(code, 0)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/menus").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @WithMockUser(authorities = "customer:list")
    void businessEmployeeCannotMaintainRoles() throws Exception {
        mvc.perform(post("/api/system/roles").contentType("application/json").content(body(code(), 1)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/system/roles/1")).andExpect(status().isForbidden());
        assign(1, List.of(), 403);
    }

    private long create(String code) throws Exception {
        String response = mvc.perform(post("/api/system/roles").contentType("application/json").content(body(code, 1)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(response).path("data").asLong();
    }

    private void assign(long id, List<Long> ids, int status) throws Exception {
        mvc.perform(post("/api/system/roles/{id}/permissions", id).contentType("application/json")
                .content(json.writeValueAsString(Map.of("permissionIds", ids))))
                .andExpect(status().is(status));
    }

    private int grantCount(long id) { return jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission WHERE role_id=?", Integer.class, id); }
    private long permissionId(String code) { return jdbc.queryForObject("SELECT id FROM sys_permission WHERE permission_code=?", Long.class, code); }
    private String code() { return "T_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(); }
    private String body(String code, int status) throws Exception {
        return json.writeValueAsString(Map.of("roleCode", code, "roleName", "测试角色", "dataScope", 3, "status", status, "remark", "事务回滚测试"));
    }
}
