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
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PermissionApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    private long operatorId;
    private long adminRoleId;
    private String token;

    @BeforeEach
    void loginAsAdministrator() throws Exception {
        String no = "T" + UUID.randomUUID().toString().substring(0,20);
        adminRoleId = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='SUPER_ADMIN'", Long.class);
        jdbc.update("INSERT INTO sys_user(employee_no,real_name,password_hash,dept_id) SELECT ?, '权限维护测试', ?, id FROM sys_dept WHERE dept_code='DEPT_HQ'", no, encoder.encode("Test@123456"));
        operatorId = jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?", Long.class, no);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", operatorId, adminRoleId);
        String login = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("employeeNo",no,"password","Test@123456"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        token = json.readTree(login).path("data").path("token").asText();
    }

    @Test
    void createUpdateStatusDeleteAndAutomaticAdminGrant() throws Exception {
        Map<String,Object> request = request(1,null);
        long id = create(request);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission WHERE role_id=? AND permission_id=?", Integer.class, adminRoleId,id));
        request.put("permissionName","修改后的目录");
        mvc.perform(put(base()+"/{id}",id).header("Authorization",auth()).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isOk());
        assertEquals("修改后的目录",jdbc.queryForObject("SELECT permission_name FROM sys_permission WHERE id=?",String.class,id));
        mvc.perform(put(base()+"/{id}/status",id).header("Authorization",auth()).contentType("application/json").content("{\"status\":0}")).andExpect(status().isOk());
        assertEquals(0,jdbc.queryForObject("SELECT status FROM sys_permission WHERE id=?",Integer.class,id));
        mvc.perform(delete(base()+"/{id}",id).header("Authorization",auth())).andExpect(status().isOk());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission WHERE id=?",Integer.class,id));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission WHERE permission_id=?",Integer.class,id));
    }

    @Test
    void rejectsCodeTypeChangesCyclesInvalidParentAndDuplicateCodes() throws Exception {
        Map<String,Object> root = request(1,null);
        long rootId = create(root);
        mvc.perform(post(base()).header("Authorization",auth()).contentType("application/json").content(json.writeValueAsString(root))).andExpect(status().isConflict());
        Map<String,Object> child = request(1,rootId);
        long childId = create(child);
        root.put("parentId",childId);
        mvc.perform(put(base()+"/{id}",rootId).header("Authorization",auth()).contentType("application/json").content(json.writeValueAsString(root))).andExpect(status().isBadRequest());
        root.put("parentId",null);
        root.put("permissionCode","menu:changed");
        mvc.perform(put(base()+"/{id}",rootId).header("Authorization",auth()).contentType("application/json").content(json.writeValueAsString(root))).andExpect(status().isBadRequest());
        Map<String,Object> invalid = request(3,rootId);
        mvc.perform(post(base()).header("Authorization",auth()).contentType("application/json").content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
        invalid = request(1,Long.MAX_VALUE);
        mvc.perform(post(base()).header("Authorization",auth()).contentType("application/json").content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
    }

    @Test
    void menuUsesExistingComponentAndUniqueNonReservedRoute() throws Exception {
        Map<String,Object> menu = request(2,null);
        menu.put("component",null);
        mvc.perform(post(base()).header("Authorization",auth()).contentType("application/json").content(json.writeValueAsString(menu))).andExpect(status().isBadRequest());
        menu.put("component","customer/index");
        menu.put("routePath","/login");
        mvc.perform(post(base()).header("Authorization",auth()).contentType("application/json").content(json.writeValueAsString(menu))).andExpect(status().isBadRequest());
        menu.put("routePath","/customer");
        mvc.perform(post(base()).header("Authorization",auth()).contentType("application/json").content(json.writeValueAsString(menu))).andExpect(status().isBadRequest());
        menu.put("routePath","/test-"+UUID.randomUUID());
        long id = create(menu);
        assertNotNull(jdbc.queryForObject("SELECT component FROM sys_permission WHERE id=?",String.class,id));
        mvc.perform(get(base()+"/components").header("Authorization",auth())).andExpect(status().isOk()).andExpect(jsonPath("$.data['customer/index']").value("客户档案"));
    }

    @Test
    void deletionRequiresNoChildrenAndNoBusinessRoleReferences() throws Exception {
        long root = create(request(1,null));
        long menu = create(request(2,root));
        long action = create(request(3,menu));
        mvc.perform(delete(base()+"/{id}",root).header("Authorization",auth())).andExpect(status().isBadRequest());
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT id,? FROM sys_role WHERE role_code='MARKETING'",action);
        mvc.perform(delete(base()+"/{id}",action).header("Authorization",auth())).andExpect(status().isBadRequest());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission WHERE id=?",Integer.class,action));
    }

    @Test
    void managementEntryIsProtected() throws Exception {
        for (String code : List.of("menu:system","menu:system:permission","system:permission:list","system:permission:delete")) {
            long id = jdbc.queryForObject("SELECT id FROM sys_permission WHERE permission_code=?",Long.class,code);
            mvc.perform(delete(base()+"/{id}",id).header("Authorization",auth())).andExpect(status().isBadRequest());
            mvc.perform(put(base()+"/{id}/status",id).header("Authorization",auth()).contentType("application/json").content("{\"status\":0}")).andExpect(status().isBadRequest());
            mvc.perform(put(base()+"/{id}",id).header("Authorization",auth()).contentType("application/json").content(json.writeValueAsString(request(1,null)))).andExpect(status().isBadRequest());
        }
    }

    @Test
    void searchPreservesAncestorsAndDisabledBranchImmediatelyLosesActions() throws Exception {
        Map<String,Object> rootRequest=request(1,null);
        long root=create(rootRequest);
        long menu=create(request(2,root));
        Map<String,Object> actionRequest=request(3,menu);
        long action=create(actionRequest);
        mvc.perform(get(base()+"/tree").param("keyword",actionRequest.get("permissionCode").toString()).param("type","3").header("Authorization",auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(root))
                .andExpect(jsonPath("$.data[0].children[0].children[0].id").value(action));
        String before=mvc.perform(get("/api/auth/me").header("Authorization",auth())).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(before.contains(actionRequest.get("permissionCode").toString()));
        mvc.perform(put(base()+"/{id}/status",root).header("Authorization",auth()).contentType("application/json").content("{\"status\":0}")).andExpect(status().isOk());
        String after=mvc.perform(get("/api/auth/me").header("Authorization",auth())).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(after.contains(actionRequest.get("permissionCode").toString()));
    }

    @Test
    void nonAdminIsRejectedEvenWithAllManagementOperationPermissions() throws Exception {
        String code="T"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO sys_role(role_name,role_code) VALUES('权限测试普通角色',?)",code);
        long role=jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code=?",Long.class,code);
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT ?,id FROM sys_permission WHERE permission_code LIKE 'system:permission:%'",role);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?",operatorId);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)",operatorId,role);
        mvc.perform(get(base()+"/tree").header("Authorization",auth())).andExpect(status().isForbidden());
        mvc.perform(post(base()).header("Authorization",auth()).contentType("application/json").content(json.writeValueAsString(request(1,null)))).andExpect(status().isForbidden());
        mvc.perform(delete(base()+"/1").header("Authorization",auth())).andExpect(status().isForbidden());
    }

    private Map<String,Object> request(int type,Long parent) {
        String suffix=UUID.randomUUID().toString().replace("-","");
        Map<String,Object> value=new HashMap<>();
        value.put("permissionName","测试权限"); value.put("permissionCode",(type==3?"test:":"menu:test:")+suffix);
        value.put("permissionType",type); value.put("parentId",parent); value.put("routePath",type==2?"/test-"+suffix:null);
        value.put("component",type==2?"customer/index":null); value.put("sortNo",0); value.put("visible",1); value.put("status",1);
        return value;
    }
    private long create(Map<String,Object> value) throws Exception {
        String result=mvc.perform(post(base()).header("Authorization",auth()).contentType("application/json").content(json.writeValueAsString(value)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(result).path("data").asLong();
    }
    private String base(){return "/api/system/permissions";}
    private String auth(){return "Bearer "+token;}
}
