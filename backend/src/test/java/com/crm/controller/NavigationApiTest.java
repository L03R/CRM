package com.crm.controller;

import com.fasterxml.jackson.databind.*;
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
class NavigationApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;

    @Test
    void administratorGetsTheAgreedTreeAndEveryPageHasAnAllowedComponent() throws Exception {
        String token=login("SUPER_ADMIN");JsonNode tree=menus(token);
        assertEquals(List.of("工作台","线索管理","客户管理","销售管理","产品管理","交付协同","数据分析","系统设置"),names(tree));
        assertEquals(List.of("工作概览","待办事项","我的日程"),names(tree.get(0).path("children")));
        assertEquals(List.of("客户档案","联系人","客户公海","跟进记录"),names(tree.get(2).path("children")));
        assertEquals(List.of("商机管理","报价管理","销售订单"),names(tree.get(3).path("children")));
        assertEquals(List.of("产品档案","产品分类"),names(tree.get(4).path("children")));
        assertEquals(List.of("订单交付进度","发货记录","售后记录"),names(tree.get(5).path("children")));
        assertEquals(List.of("线索转化分析","客户与跟进分析","销售商机分析"),names(tree.get(6).path("children")));
        assertEquals(List.of("员工管理","部门管理","角色管理","权限管理","操作日志"),names(tree.get(7).path("children")));
        JsonNode components=data(mvc.perform(get("/api/system/permissions/components").header("Authorization",auth(token))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        List<JsonNode> pages=new ArrayList<>();flatten(tree,pages);
        assertEquals(24,pages.size());Set<String> paths=new HashSet<>();
        for(JsonNode page:pages){assertTrue(components.has(page.path("component").asText()));assertTrue(paths.add(page.path("routePath").asText()));}
    }

    @Test
    void businessRolesGetTheirOwnMenuBranches() throws Exception {
        Map<String,List<String>> expected=Map.of(
                "SALES_STAFF",List.of("工作台","线索管理","客户管理","销售管理","产品管理","交付协同","数据分析"),
                "SALES_MANAGER",List.of("工作台","线索管理","客户管理","销售管理","产品管理","交付协同","数据分析"),
                "MARKETING",List.of("工作台","线索管理","产品管理","数据分析"),
                "PRODUCTION",List.of("工作台","产品管理","交付协同"),
                "SUPPLY_STAFF",List.of("工作台","产品管理","交付协同"),
                "FINANCE",List.of("工作台"),
                "CUSTOMER_SERVICE",List.of("工作台","客户管理","销售管理","产品管理","交付协同","数据分析"),
                "PRODUCT_MANAGER",List.of("工作台","产品管理"));
        for(var role:expected.entrySet())assertEquals(role.getValue(),names(menus(login(role.getKey()))),role.getKey());
        String finance=login("FINANCE");
        mvc.perform(get("/api/products").header("Authorization",auth(finance))).andExpect(status().isForbidden());
        mvc.perform(get("/api/system/users").header("Authorization",auth(login("PRODUCTION")))).andExpect(status().isForbidden());
    }

    @Test
    void oldCustomerAndSalesOperationsKeepTheirOriginalPagesAndCorrectParents() {
        assertEquals("/customer",jdbc.queryForObject("SELECT route_path FROM sys_permission WHERE permission_code='menu:customer:archive'",String.class));
        assertEquals("/business",jdbc.queryForObject("SELECT route_path FROM sys_permission WHERE permission_code='menu:business'",String.class));
        assertEquals("/order",jdbc.queryForObject("SELECT route_path FROM sys_permission WHERE permission_code='menu:order'",String.class));
        for(String code:List.of("customer:list","customer:add","customer:edit","customer:delete"))
            assertEquals("menu:customer:archive",jdbc.queryForObject("SELECT p.permission_code FROM sys_permission a JOIN sys_permission p ON p.id=a.parent_id WHERE a.permission_code=?",String.class,code));
    }

    @Test
    void disabledCollaborationBranchDisappearsImmediatelyForExistingSession() throws Exception {
        String token=login("SUPPLY_STAFF");assertTrue(names(menus(token)).contains("交付协同"));
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code='menu:delivery'");
        assertFalse(names(menus(token)).contains("交付协同"));
        String profile=mvc.perform(get("/api/auth/me").header("Authorization",auth(token))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(profile.contains("shipment:list"));
        mvc.perform(get("/api/products").header("Authorization",auth(token))).andExpect(status().isOk());
    }

    @Test
    void customRoleCustomerAuthorityStillBuildsTheNewHierarchyAndRolelessUsersHaveNoMenus() throws Exception {
        String role=code();jdbc.update("INSERT INTO sys_role(role_code,role_name) VALUES(?,?)",role,"目录测试角色");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code='customer:list' WHERE r.role_code=?",role);
        JsonNode tree=menus(login(role));
        assertEquals(List.of("客户管理"),names(tree));assertEquals(List.of("客户档案"),names(tree.get(0).path("children")));
        assertTrue(menus(login(null)).isEmpty());
    }

    private String login(String role) throws Exception {
        String no=code();long dept=jdbc.queryForObject("SELECT id FROM sys_dept WHERE dept_code='DEPT_HQ'",Long.class);
        jdbc.update("INSERT INTO sys_user(employee_no,real_name,password_hash,dept_id) VALUES(?,?,?,?)",no,"目录测试员工",encoder.encode("Nav@123456"),dept);
        if(role!=null)jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT u.id,r.id FROM sys_user u JOIN sys_role r ON r.role_code=? WHERE u.employee_no=?",role,no);
        return data(mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("employeeNo",no,"password","Nav@123456")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("token").asText();
    }
    private JsonNode menus(String token) throws Exception {return data(mvc.perform(get("/api/auth/menus").header("Authorization",auth(token))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));}
    private List<String> names(JsonNode nodes){List<String> names=new ArrayList<>();nodes.forEach(n->names.add(n.path("permissionName").asText()));return names;}
    private void flatten(JsonNode nodes,List<JsonNode> pages){for(JsonNode node:nodes){if(node.path("permissionType").asInt()==2)pages.add(node);flatten(node.path("children"),pages);}}
    private JsonNode data(String value) throws Exception{return json.readTree(value).path("data");}
    private String auth(String token){return "Bearer "+token;}
    private String code(){return "NAV"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase();}
}
