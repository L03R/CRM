package com.crm.controller;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
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
class DashboardApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    record Actor(long id,String token) {}
    Actor first,second;
    long dept;

    @BeforeEach void prepare() throws Exception {
        dept=jdbc.queryForObject("SELECT id FROM sys_dept WHERE dept_code='DEPT_SALES_EAST'",Long.class);
        first=actor("SALES_STAFF");second=actor("SALES_STAFF");
        seed(first);seed(second);
    }

    @Test void reportsAndWorkbenchOnlyIncludeCurrentSalesData() throws Exception {
        for(String kind:List.of("lead","customer","business")) {
            JsonNode report=read(reportUrl(kind),first,200);
            assertEquals(1,report.path("distribution").get(0).path("value").asInt());
            assertEquals(1,report.path("trend").get(0).path("value").asInt());
        }
        JsonNode overview=read("/api/dashboard/overview",first,200);
        assertEquals(1,overview.path("metrics").path("customerTotal").asInt());
        assertEquals(1,overview.path("metrics").path("activeBusiness").asInt());
        assertEquals(1,overview.path("metrics").path("overdueFollow").asInt());
        assertFalse(overview.toString().contains("客户"+second.id));
        assertEquals(1,read(reportUrl("customer"),first,200).path("metrics").path("overdueFollow").asInt());
    }

    @Test void reportAndBusinessReadRangesAreIntersectedInBothDirections() throws Exception {
        grantExtra(first,"report:customer:list",1);
        assertEquals(1,read(reportUrl("customer"),first,200).path("distribution").get(0).path("value").asInt());
        grantExtra(second,"customer:list",1);
        assertEquals(1,read(reportUrl("customer"),second,200).path("distribution").get(0).path("value").asInt());
    }

    @Test void sourcePermissionAndEnabledReportBranchAreBothRequired() throws Exception {
        jdbc.update("DELETE rp FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE r.role_code='SALES_STAFF' AND p.permission_code='customer:list'");
        read(reportUrl("customer"),first,403);
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code='menu:analysis'");
        read(reportUrl("lead"),first,403);
        assertTrue(read("/api/dashboard/overview",first,200).path("metrics").has("leadTotal"));
    }

    @Test void personalSchedulesPersistAndCannotBeReadOrModifiedByAnotherEmployee() throws Exception {
        Map<String,Object> form=schedule();
        long id=postSchedule(first,form,200).asLong();
        String url="/api/dashboard/schedules?start=2026-11-01&end=2026-11-30";
        assertEquals(1,read(url,first,200).size());assertEquals(0,read(url,second,200).size());
        mvc.perform(put("/api/dashboard/schedules/"+id).header("Authorization",auth(second)).contentType("application/json").content(json.writeValueAsString(form))).andExpect(status().isForbidden());
        form.put("status","DONE");
        mvc.perform(put("/api/dashboard/schedules/"+id).header("Authorization",auth(first)).contentType("application/json").content(json.writeValueAsString(form))).andExpect(status().isOk());
        assertEquals("DONE",read(url,first,200).get(0).path("status").asText());
    }

    @Test void taskListPaginatesBeyondPreviewAndFiltersWithoutCrossingSalesScope() throws Exception {
        for(int index=0;index<12;index++)
            jdbc.update("INSERT INTO crm_lead(company_name,customer_type,region,source,contact_name,phone,email,contact_key,demand,target_dept_id,owner_id,created_by,status,next_follow_at) VALUES(?,'STORE','上海','测试','联系人','000','','000','测试',?,?,?,'CONTACTING',CURRENT_DATE)","待办"+first.id+"-"+index,dept,first.id,first.id);
        JsonNode tasks=read("/api/dashboard/tasks?page=1&size=10",first,200);
        assertEquals(13,tasks.path("total").asInt());assertEquals(10,tasks.path("records").size());
        assertEquals(3,read("/api/dashboard/tasks?page=2&size=10",first,200).path("records").size());
        assertEquals(12,read("/api/dashboard/tasks?type=LEAD_FOLLOW&due=TODAY",first,200).path("total").asInt());
        assertEquals(0,read("/api/dashboard/tasks?keyword=待办"+first.id,second,200).path("total").asInt());
        assertEquals(5,read("/api/dashboard/overview",first,200).path("todos").size());
        read("/api/dashboard/tasks?type=UNKNOWN",first,400);
    }

    @Test void agendaUsesSelectedDatesAndCurrentBusinessScope() throws Exception {
        String tomorrow=java.time.LocalDate.now().plusDays(1).toString();
        jdbc.update("UPDATE crm_customer SET next_follow_at=TIMESTAMP(?,'10:00:00') WHERE owner_id IN(?,?)",tomorrow,first.id,second.id);
        JsonNode agenda=read("/api/dashboard/agenda?start="+tomorrow+"&end="+tomorrow,first,200);
        assertEquals(1,agenda.size());assertEquals("客户"+first.id,agenda.get(0).path("title").asText());
        String today=java.time.LocalDate.now().toString();
        assertEquals(0,read("/api/dashboard/agenda?start="+today+"&end="+today,first,200).size());
    }

    @Test void threeWorkbenchPagesCanBeRevokedIndependently() throws Exception {
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code='menu:workbench:overview'");
        read("/api/dashboard/overview",first,403);
        read("/api/dashboard/tasks",first,200);
        read("/api/dashboard/schedules?start=2026-11-01&end=2026-11-30",first,200);
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code='menu:workbench:todo'");
        read("/api/dashboard/tasks",first,403);
        postSchedule(first,schedule(),200);
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code='menu:workbench:schedule'");
        postSchedule(first,schedule(),403);
        read(reportUrl("customer"),first,200);
    }

    @Test void invalidScheduleDatesStatusAndReportRangeAreRejected() throws Exception {
        var form=schedule();form.put("endAt","2026-11-14T08:00:00");postSchedule(first,form,400);
        form=schedule();form.put("remindAt","2026-11-14T12:00:00");postSchedule(first,form,400);
        form=schedule();form.put("status","UNKNOWN");postSchedule(first,form,400);
        read("/api/dashboard/reports/lead?start=2020-02-01&end=2020-01-01",first,400);
        read("/api/dashboard/reports/lead?start=2020-01-01&end=2022-01-01",first,400);
    }

    @Test void workbenchRequiresAnEnabledEntryAndFinanceHasNoBusinessFigures() throws Exception {
        Actor finance=actor("FINANCE");
        JsonNode overview=read("/api/dashboard/overview",finance,200);
        assertTrue(overview.path("metrics").isEmpty());assertTrue(overview.path("todos").isEmpty());
        read(reportUrl("customer"),finance,403);
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code='menu:workbench'");
        read("/api/dashboard/overview",first,403);postSchedule(first,schedule(),403);
    }

    private Map<String,Object> schedule() {
        Map<String,Object> form=new HashMap<>();
        form.put("title","个人日程测试");form.put("description","测试写入事务回滚");
        form.put("startAt","2026-11-14T10:00:00");form.put("endAt","2026-11-14T11:00:00");
        form.put("remindAt","2026-11-14T09:50:00");form.put("priority","HIGH");form.put("status","PENDING");return form;
    }
    private void seed(Actor user) {
        jdbc.update("INSERT INTO crm_lead(company_name,customer_type,region,source,contact_name,phone,email,contact_key,demand,target_dept_id,owner_id,created_by,status,created_at) VALUES(?,'STORE','上海','测试来源','联系人','000','','000','测试',?,?,?,'CONTACTING','2020-01-10')","线索"+user.id,dept,user.id,user.id);
        jdbc.update("INSERT INTO crm_customer(company_name,customer_type,region,source,contact_name,phone,email,owner_id,pool_dept_id,created_by,next_follow_at,created_at) VALUES(?,'STORE','上海','测试','联系人','000','',?,?,?,NOW()-INTERVAL 1 DAY,'2020-01-10')","客户"+user.id,user.id,dept,user.id);
        long customer=jdbc.queryForObject("SELECT id FROM crm_customer WHERE company_name=?",Long.class,"客户"+user.id);
        jdbc.update("INSERT INTO crm_opportunity(customer_id,title,expected_amount,stage,created_by,created_at) VALUES(?,'测试采购',100,'INITIAL',?,'2020-01-10')",customer,user.id);
    }
    private void grantExtra(Actor actor,String permission,int scope) {
        String code="DR"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO sys_role(role_code,role_name,data_scope) VALUES(?,'分析测试角色',?)",code,scope);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=?",actor.id,code);
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code=? WHERE r.role_code=?",permission,code);
    }
    private Actor actor(String role) throws Exception {
        String no="DB"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        jdbc.update("INSERT INTO sys_user(employee_no,real_name,password_hash,dept_id) VALUES(?,'工作台测试员工',?,?)",no,encoder.encode("Dash@123456"),dept);
        long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?",Long.class,no);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=?",id,role);
        var result=mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("employeeNo",no,"password","Dash@123456")))).andExpect(status().isOk()).andReturn();
        return new Actor(id,data(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("token").asText());
    }
    private JsonNode postSchedule(Actor user,Map<String,Object> form,int status) throws Exception {
        return data(mvc.perform(post("/api/dashboard/schedules").header("Authorization",auth(user)).contentType("application/json").content(json.writeValueAsString(form))).andExpect(status().is(status)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    private String reportUrl(String kind){return "/api/dashboard/reports/"+kind+"?start=2020-01-01&end=2020-01-31";}
    private String auth(Actor user){return "Bearer "+user.token;}
    private JsonNode read(String url,Actor user,int status) throws Exception {return data(mvc.perform(get(url).header("Authorization",auth(user))).andExpect(status().is(status)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));}
    private JsonNode data(String text) throws Exception {return json.readTree(text).path("data");}
}
