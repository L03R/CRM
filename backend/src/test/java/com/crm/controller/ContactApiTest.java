package com.crm.controller;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class ContactApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    record Actor(long id,String token) {}
    Actor first,second,manager;
    long customer,other,primary,otherPrimary;

    @BeforeEach void prepare() throws Exception {
        first=actor("SALES_STAFF","DEPT_SALES_EAST");second=actor("SALES_STAFF","DEPT_SALES_EAST");manager=actor("SALES_MANAGER","DEPT_SALES");
        customer=send(post("/api/customers"),first,customerBody("甲"+first.id),200).asLong();
        other=send(post("/api/customers"),second,customerBody("乙"+second.id),200).asLong();
        primary=primary(customer);otherPrimary=primary(other);
    }

    @Test void multipleContactsMainSwitchSnapshotAndRetirementStayConsistent() throws Exception {
        var body=contact(customer,"采购负责人");body.put("phone","");body.put("wechat","test-wechat");
        long id=send(post("/api/contacts"),first,body,200).asLong();
        assertEquals(2,page(customer,first).path("total").asInt());
        assertEquals(primary,primary(customer));
        send(post("/api/contacts/"+id+"/primary"),first,null,200);
        assertEquals("采购负责人",get("/api/customers/"+customer,first,200).path("contactName").asText());
        assertEquals("",get("/api/customers/"+customer,first,200).path("phone").asText());
        send(post("/api/customers/"+customer+"/follows"),first,follow(id),200);
        body.put("contactName","采购负责人改名");send(put("/api/contacts/"+id),first,body,200);
        assertEquals("采购负责人",get("/api/customers/"+customer+"/follows",first,200).get(0).path("contactNameSnapshot").asText());
        send(put("/api/contacts/"+id+"/status?status=0"),first,null,200);
        assertEquals(primary,primary(customer));
        send(post("/api/contacts/"+id+"/primary"),first,null,400);
        send(post("/api/customers/"+customer+"/follows"),first,follow(id),400);
        assertEquals(1,get("/api/contacts?customerId="+customer+"&status=1",first,200).path("total").asInt());
    }

    @Test void otherSalesCannotReadWriteOrUseContactsFromAnotherCustomer() throws Exception {
        get("/api/contacts?customerId="+customer,second,403);
        assertEquals(0,get("/api/contacts?keyword=甲"+first.id,second,200).path("total").asInt());
        send(put("/api/contacts/"+primary),second,contact(customer,"越权修改"),403);
        send(post("/api/contacts/"+primary+"/primary"),second,null,403);
        send(put("/api/contacts/"+primary+"/status?status=0"),second,null,403);
        send(post("/api/contacts"),second,contact(customer,"越权新增"),403);
        send(post("/api/customers/"+customer+"/follows"),first,follow(otherPrimary),400);
        send(put("/api/contacts/"+primary),first,contact(other,"更换所属客户"),403);
    }

    @Test void customerTransferMovesContactAndHistoryAccess() throws Exception {
        send(post("/api/customers/"+customer+"/follows"),first,follow(primary),200);
        send(post("/api/customers/"+customer+"/assign"),manager,Map.of("ownerId",second.id,"reason","测试客户交接"),200);
        get("/api/contacts?customerId="+customer,first,403);
        assertEquals(1,page(customer,second).path("total").asInt());
        assertEquals("原联系人",get("/api/customers/"+customer+"/follows",second,200).get(0).path("contactNameSnapshot").asText());
    }

    @Test void publicPoolContactsAreReadableWithinRegionButCannotBeEditedUntilClaimed() throws Exception {
        long request=send(post("/api/customers/"+customer+"/pool-requests"),first,Map.of("reason","测试公海交接"),200).asLong();
        send(post("/api/customers/"+customer+"/pool-requests/"+request+"/review"),manager,Map.of("approve",true,"note","确认可重新安排"),200);
        assertEquals(1,page(customer,first).path("total").asInt());
        send(put("/api/contacts/"+primary),first,contact(customer,"公海修改"),403);
        send(post("/api/customers/"+customer+"/claim"),second,null,200);
        get("/api/contacts?customerId="+customer,first,403);
        assertEquals(1,page(customer,second).path("total").asInt());
    }

    @Test void contactAndCustomerReadScopesAreIntersected() throws Exception {
        extra(first,"contact:list");extra(second,"customer:list");
        assertEquals(1,get("/api/contacts",first,200).path("total").asInt());
        assertEquals(1,get("/api/contacts",second,200).path("total").asInt());
        get("/api/contacts?customerId="+other,first,403);
        get("/api/contacts?customerId="+customer,second,403);
    }

    @Test void contactPermissionsAndStatusChangesAreCheckedOnServer() throws Exception {
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code='contact:status'");
        var body=contact(customer,"原联系人");body.put("status",0);
        send(put("/api/contacts/"+primary),first,body,403);
        send(put("/api/contacts/"+primary+"/status?status=0"),first,null,403);
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code='menu:customer:contact'");
        get("/api/contacts",first,403);
        assertEquals("原联系人",get("/api/customers/"+customer,first,200).path("contactName").asText());
    }

    @Test void conversionCreatesExactlyOnePrimaryContactAndKeepsLeadCommunication() throws Exception {
        var body=customerBody("转化"+first.id);body.put("targetDeptId",jdbc.queryForObject("SELECT dept_id FROM sys_user WHERE id=?",Long.class,first.id));body.put("demand","测试木吉他采购");
        long lead=send(post("/api/leads"),first,body,200).asLong();
        send(post("/api/leads/"+lead+"/follows"),first,follow(primary),400);
        var communication=follow(primary);communication.remove("contactId");send(post("/api/leads/"+lead+"/follows"),first,communication,200);
        long converted=send(post("/api/leads/"+lead+"/convert"),first,null,200).asLong();
        assertEquals(converted,send(post("/api/leads/"+lead+"/convert"),first,null,200).asLong());
        assertEquals(1,page(converted,first).path("total").asInt());
        assertEquals("原联系人",get("/api/customers/"+converted+"/follows",first,200).get(0).path("contactNameSnapshot").asText());
    }

    @Test void invalidContactsAndStaleCustomerFormCannotOverwriteContactRecords() throws Exception {
        var body=contact(customer,"无联系方式");body.put("phone","");body.put("email","");send(post("/api/contacts"),first,body,400);
        body=contact(customer,"无效邮箱");body.put("email","invalid");send(post("/api/contacts"),first,body,400);
        var client=customerBody("甲"+first.id);client.put("contactName","过期表单姓名");client.put("phone","111");send(put("/api/customers/"+customer),first,client,200);
        assertEquals("原联系人",get("/api/customers/"+customer,first,200).path("contactName").asText());
        client.remove("contactName");client.remove("phone");client.remove("email");send(put("/api/customers/"+customer),first,client,200);
        send(put("/api/contacts/"+primary+"/status?status=0"),first,null,200);
        assertEquals("",get("/api/customers/"+customer,first,200).path("contactName").asText());
        send(put("/api/contacts/"+primary+"/status?status=1"),first,null,200);assertEquals(primary,primary(customer));
    }

    private long primary(long id){return jdbc.queryForObject("SELECT id FROM crm_contact WHERE customer_id=? AND is_primary=1",Long.class,id);}
    private JsonNode page(long id,Actor user) throws Exception{return get("/api/contacts?customerId="+id,user,200);}
    private Map<String,Object> contact(long id,String name){return new HashMap<>(Map.of("customerId",id,"contactName",name,"jobTitle","采购","phone","00000000002","landline","","wechat","","email","contact@example.com","status",1,"remark","事务回滚测试"));}
    private Map<String,Object> customerBody(String name){return new HashMap<>(Map.of("companyName",name,"customerType","STORE","region","上海","source","测试","contactName","原联系人","phone","00000000001","email","original@example.com","relationship","POTENTIAL","protectedCustomer",false));}
    private Map<String,Object> follow(long id){return new HashMap<>(Map.of("method","PHONE","content","测试联系人沟通","occurredAt",LocalDateTime.now().minusMinutes(1).toString(),"contactId",id));}
    private void extra(Actor user,String permission){String code="CT"+UUID.randomUUID().toString().replace("-","");jdbc.update("INSERT INTO sys_role(role_code,role_name,data_scope) VALUES(?,'联系人测试角色',1)",code);jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=?",user.id,code);jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code=? WHERE r.role_code=?",permission,code);}
    private Actor actor(String role,String department) throws Exception {String no="CT"+UUID.randomUUID().toString().replace("-","").substring(0,20);long dept=jdbc.queryForObject("SELECT id FROM sys_dept WHERE dept_code=?",Long.class,department);jdbc.update("INSERT INTO sys_user(employee_no,real_name,password_hash,dept_id) VALUES(?,'联系人测试员工',?,?)",no,encoder.encode("Contact@123456"),dept);long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?",Long.class,no);jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=?",id,role);String token=send(post("/api/auth/login"),null,Map.of("employeeNo",no,"password","Contact@123456"),200).path("token").asText();return new Actor(id,token);}
    private JsonNode get(String url,Actor user,int status) throws Exception {return send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url),user,null,status);}
    private JsonNode send(MockHttpServletRequestBuilder request,Actor user,Object body,int status) throws Exception {if(user!=null)request.header("Authorization","Bearer "+user.token);if(body!=null)request.contentType("application/json").content(json.writeValueAsString(body));return json.readTree(mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");}
}
