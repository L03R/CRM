package com.crm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = {"system:dept:list", "system:dept:add", "system:dept:edit", "system:dept:delete"})
class DeptApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void createReadUpdateDelete() throws Exception {
        long id = create(null, "测试部门");
        mvc.perform(get("/api/depts/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.deptName").value("测试部门"));
        Map<String, Object> update = body(null, "修改后的部门");
        mvc.perform(put("/api/depts/{id}", id).contentType("application/json").content(json.writeValueAsString(update)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/depts/{id}", id))
                .andExpect(jsonPath("$.data.deptName").value("修改后的部门"));
        mvc.perform(delete("/api/depts/{id}", id)).andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM sys_dept WHERE id=?", Integer.class, id));
        mvc.perform(get("/api/depts/{id}", id)).andExpect(status().isBadRequest());
    }

    @Test
    void searchPreservesAncestorsAndPreventsCycles() throws Exception {
        String keyword = "测试" + UUID.randomUUID();
        long root = create(null, "测试根部门");
        long child = create(root, keyword);
        mvc.perform(get("/api/depts").param("keyword", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(root))
                .andExpect(jsonPath("$.data[0].children[0].id").value(child));
        mvc.perform(put("/api/depts/{id}", root).contentType("application/json")
                        .content(json.writeValueAsString(body(child, "测试根部门"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不能将部门移动到自身或下级部门"));
        mvc.perform(delete("/api/depts/{id}", root)).andExpect(status().isBadRequest());
    }

    @Test
    void employeesPreventDeletionAndMemberCountIncludesOnlyEmployedUsers() throws Exception {
        long id = create(null, "测试人员部门");
        jdbc.update("INSERT INTO sys_user(employee_no,real_name,password_hash,dept_id,employment_status) VALUES(?,?,?,?,0)",
                "T" + UUID.randomUUID().toString().substring(0, 20), "测试员工", "test-only-hash", id);
        mvc.perform(get("/api/depts/{id}", id)).andExpect(jsonPath("$.data.memberCount").value(0));
        mvc.perform(delete("/api/depts/{id}", id)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先迁移部门员工（包含离职员工）"));
    }

    @Test
    void validatesRequiredFieldsAndParent() throws Exception {
        mvc.perform(post("/api/depts").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/depts").contentType("application/json")
                        .content(json.writeValueAsString(body(Long.MAX_VALUE, "无效父部门"))))
                .andExpect(status().isBadRequest());
    }

    private long create(Long parentId, String name) throws Exception {
        String response = mvc.perform(post("/api/depts").contentType("application/json")
                        .content(json.writeValueAsString(body(parentId, name))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("data").asLong();
    }

    private Map<String, Object> body(Long parentId, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("parentId", parentId);
        body.put("deptName", name);
        body.put("deptCode", "TEST_" + UUID.randomUUID());
        body.put("sortNo", 0);
        body.put("status", 1);
        return body;
    }
}
