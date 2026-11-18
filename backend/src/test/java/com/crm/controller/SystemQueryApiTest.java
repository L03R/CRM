package com.crm.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SystemQueryApiTest {
    @Autowired MockMvc mvc;

    @Test
    @WithMockUser(authorities = {"system:employee:list", "system:role:list"})
    void readsDatabaseEmployeesRolesAndPermissionsWithoutPasswords() throws Exception {
        mvc.perform(get("/api/system/users").param("keyword", "ADMIN001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].employeeNo").value("ADMIN001"))
                .andExpect(jsonPath("$.data[0].roleIds").isArray())
                .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/system/roles").param("keyword", "SUPER_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].roleCode").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.data[0].permissionIds").isArray());
        mvc.perform(get("/api/system/permissions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @WithMockUser(authorities = "order:list")
    void businessEmployeeCannotReadSystemDirectories() throws Exception {
        for (String path : new String[]{"users", "roles", "permissions"}) {
            mvc.perform(get("/api/system/" + path)).andExpect(status().isForbidden());
        }
    }
}
