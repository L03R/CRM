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
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class QuotationApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    record Actor(long id,String token) {}
    Actor first,second,manager;
    long customer,other,business,otherBusiness,anotherBusiness,contact,otherContact,sku,product;

    @BeforeEach void prepare() throws Exception {
        first=actor("SALES_STAFF","DEPT_SALES_EAST");second=actor("SALES_STAFF","DEPT_SALES_EAST");manager=actor("SALES_MANAGER","DEPT_SALES");
        customer=client(first);other=client(second);contact=primary(customer);otherContact=primary(other);
        business=business(customer,first);anotherBusiness=business(customer,first);otherBusiness=business(other,second);
        String code=code();jdbc.update("INSERT INTO crm_product_category(category_code,category_name) VALUES(?,'报价测试品类')",code);
        long category=jdbc.queryForObject("SELECT id FROM crm_product_category WHERE category_code=?",Long.class,code);
        jdbc.update("INSERT INTO crm_product(category_id,model_code,product_name,brand,specifications,unit,supply_type) VALUES(?,?,'测试吉他','测试品牌','原始规格','把',1)",category,code);
        product=jdbc.queryForObject("SELECT id FROM crm_product WHERE model_code=?",Long.class,code);
        jdbc.update("INSERT INTO crm_product_sku(product_id,sku_code,sale_price) VALUES(?,?,34.56)",product,code);sku=jdbc.queryForObject("SELECT id FROM crm_product_sku WHERE sku_code=?",Long.class,code);
    }

    @Test void fullQuoteLifecycleRevisionAndAmountsAreReal() throws Exception {
        var body=body(customer,business,contact);body.put("status","ACCEPTED");body.put("isFinal",true);body.put("totalAmount",1);
        long id=send(post("/api/quotations"),first,body,200).asLong();
        var initial=detail(id,first);assertEquals("DRAFT",initial.path("status").asText());assertFalse(initial.path("isFinal").asBoolean());assertEquals("69.12",initial.path("totalAmount").asText());
        action(id,"FINAL",first,400);action(id,"SEND",first,200);
        send(put("/api/quotations/"+id),first,body,400);
        long next=send(post("/api/quotations/"+id+"/revision"),first,Map.of("reason","客户调整配置"),200).asLong();
        assertEquals(next,send(post("/api/quotations/"+id+"/revision"),first,Map.of("reason","重复点击"),200).asLong());
        assertEquals(2,detail(next,first).path("versionNo").asInt());
        body.put("items",List.of(item(3,"12.34")));send(put("/api/quotations/"+next),first,body,200);
        action(next,"SEND",first,200);action(next,"ACCEPT",first,200);action(next,"FINAL",first,200);
        assertEquals("37.02",detail(next,first).path("totalAmount").asText());assertTrue(detail(next,first).path("isFinal").asBoolean());
        assertEquals("69.12",detail(id,first).path("totalAmount").asText());assertEquals(2,detail(next,first).path("versions").size());
        assertEquals("QUALIFIED",jdbc.queryForObject("SELECT stage FROM crm_opportunity WHERE id=?",String.class,business));
    }

    @Test void oneFinalPerOpportunityAllowsDifferentFinalsForSameCustomer() throws Exception {
        long a=create(customer,business,contact,first),b=create(customer,business,contact,first),c=create(customer,anotherBusiness,contact,first);
        accepted(a);accepted(b);accepted(c);action(a,"FINAL",first,200);action(b,"FINAL",first,200);action(c,"FINAL",first,200);
        assertFalse(detail(a,first).path("isFinal").asBoolean());assertTrue(detail(b,first).path("isFinal").asBoolean());assertTrue(detail(c,first).path("isFinal").asBoolean());
        assertEquals(2,get("/api/quotations?customerId="+customer+"&onlyFinal=true",first,200).path("total").asInt());
        assertTrue(detail(a,first).path("events").toString().contains("UNFINAL"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE crm_quotation SET is_final=1 WHERE id=?",a));
    }

    @Test void customerOpportunityAndContactMustMatchAndStayBound() throws Exception {
        var request=body(customer,otherBusiness,contact);send(post("/api/quotations"),first,request,400);
        request=body(customer,business,otherContact);send(post("/api/quotations"),first,request,400);
        long id=create(customer,business,contact,first);
        request=body(other,otherBusiness,otherContact);send(put("/api/quotations/"+id),first,request,400);
        assertEquals(customer,detail(id,first).path("customerId").asLong());
        jdbc.update("UPDATE crm_contact SET status=0,is_primary=0 WHERE id=?",contact);
        action(id,"SEND",first,400);
    }

    @Test void twoSalesCannotReadOrOperateEachOthersQuotes() throws Exception {
        long id=create(customer,business,contact,first);
        get("/api/quotations/"+id,second,403);send(put("/api/quotations/"+id),second,body(customer,business,contact),403);
        action(id,"SEND",second,403);action(id,"VOID",second,403);
        send(post("/api/quotations/"+id+"/revision"),second,Map.of("reason","越权修订"),403);
        assertEquals(0,get("/api/quotations?customerId="+customer,second,200).path("total").asInt());
        assertEquals(0,get("/api/quotations/opportunities?customerId="+customer,second,200).size());
        get("/api/quotations/contacts?customerId="+customer,second,403);
        send(post("/api/quotations"),second,body(customer,business,contact),403);
    }

    @Test void sentSnapshotsSurviveProductContactAndCustomerChanges() throws Exception {
        long id=create(customer,business,contact,first);action(id,"SEND",first,200);var original=detail(id,first);
        jdbc.update("UPDATE crm_product SET product_name='新的产品名',specifications='新的规格',unit='套',status=0 WHERE id=?",product);
        jdbc.update("UPDATE crm_contact SET contact_name='新的联系人',phone='111' WHERE id=?",contact);
        jdbc.update("UPDATE crm_customer SET company_name=? WHERE id=?","新的客户名"+first.id,customer);
        var saved=detail(id,first);assertEquals(original.path("customerName"),saved.path("customerName"));assertEquals("原联系人",saved.path("contactName").asText());
        assertEquals("测试吉他",saved.path("items").get(0).path("productName").asText());assertEquals("原始规格",saved.path("items").get(0).path("specification").asText());
        long next=send(post("/api/quotations/"+id+"/revision"),first,Map.of("reason","调整产品"),200).asLong();
        action(next,"SEND",first,400);
    }

    @Test void expiredAndFutureQuotesCannotBeSentOrAcceptedIncorrectly() throws Exception {
        var request=body(customer,business,contact);request.put("quoteDate",LocalDate.now().minusDays(3).toString());request.put("validUntil",LocalDate.now().minusDays(1).toString());
        long id=send(post("/api/quotations"),first,request,200).asLong();action(id,"SEND",first,400);
        jdbc.update("UPDATE crm_quotation SET status='SENT' WHERE id=?",id);
        assertEquals("EXPIRED",detail(id,first).path("displayStatus").asText());
        assertEquals(1,get("/api/quotations?status=EXPIRED",first,200).path("total").asInt());action(id,"ACCEPT",first,400);
        request=body(customer,business,contact);request.put("quoteDate",LocalDate.now().plusDays(1).toString());
        long future=send(post("/api/quotations"),first,request,200).asLong();action(future,"SEND",first,400);
    }

    @Test void serverRejectsInvalidItemsDatesAndOverflowWithoutWritingDrafts() throws Exception {
        var request=body(customer,business,contact);request.put("items",List.of(item(0,"1.00")));send(post("/api/quotations"),first,request,400);
        request.put("items",List.of(item(1,"-1.00")));send(post("/api/quotations"),first,request,400);
        request.put("items",List.of(item(1,"0.001")));send(post("/api/quotations"),first,request,400);
        request.put("items",List.of(item(1000000,"99999999.99")));send(post("/api/quotations"),first,request,400);
        request=body(customer,business,contact);request.put("validUntil",LocalDate.now().minusDays(1).toString());send(post("/api/quotations"),first,request,400);
        request=body(customer,business,contact);request.put("items",List.of());send(post("/api/quotations"),first,request,400);
        assertEquals(0,get("/api/quotations",first,200).path("total").asInt());
    }

    @Test void voidingFinalQuoteClearsSelectionAndAllowsPoolAfterBusinessEnds() throws Exception {
        long id=create(customer,business,contact,first);accepted(id);action(id,"FINAL",first,200);
        jdbc.update("UPDATE crm_opportunity SET stage='WON',close_reason='测试正式成交' WHERE customer_id=?",customer);
        send(post("/api/customers/"+customer+"/pool-requests"),first,Map.of("reason","申请重新分配"),400);
        action(id,"VOID",first,200);assertFalse(detail(id,first).path("isFinal").asBoolean());
        long request=send(post("/api/customers/"+customer+"/pool-requests"),first,Map.of("reason","申请重新分配"),200).asLong();
        send(post("/api/customers/"+customer+"/pool-requests/"+request+"/review"),manager,Map.of("approve",true,"note","已确认报价作废"),200);
        get("/api/quotations/"+id,first,403);send(post("/api/customers/"+customer+"/claim"),second,null,200);
        assertEquals("VOID",detail(id,second).path("status").asText());
    }

    @Test void customerTransferKeepsQuotesAndChangesAccess() throws Exception {
        long id=create(customer,business,contact,first);accepted(id);action(id,"FINAL",first,200);
        send(post("/api/customers/"+customer+"/assign"),manager,Map.of("ownerId",second.id,"reason","业务交接"),200);
        get("/api/quotations/"+id,first,403);assertEquals(customer,detail(id,second).path("customerId").asLong());assertTrue(detail(id,second).path("isFinal").asBoolean());
    }

    @Test void exactActionAndLiveMenuPermissionsAreEnforced() throws Exception {
        long id=create(customer,business,contact,first);
        jdbc.update("DELETE rp FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE r.role_code='SALES_STAFF' AND p.permission_code='quotation:void'");
        action(id,"VOID",first,403);action(id,"SEND",first,200);
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code='menu:sales:quotation'");
        get("/api/quotations/"+id,first,403);get("/api/quotations",first,403);action(id,"ACCEPT",first,403);
    }

    @Test void quoteCustomerAndBusinessRangesAreIntersected() throws Exception {
        long id=create(customer,business,contact,first);long otherId=create(other,otherBusiness,otherContact,second);
        extra(first,"quotation:list");extra(second,"customer:list");extra(second,"business:list");
        assertEquals(1,get("/api/quotations",first,200).path("total").asInt());assertEquals(1,get("/api/quotations",second,200).path("total").asInt());
        get("/api/quotations/"+otherId,first,403);get("/api/quotations/"+id,second,403);
    }

    @Test void closedBusinessesRejectNewDraftsAndOnlyLatestVersionCanBeRevised() throws Exception {
        long id=create(customer,business,contact,first);action(id,"SEND",first,200);
        long next=send(post("/api/quotations/"+id+"/revision"),first,Map.of("reason","第二版"),200).asLong();action(next,"SEND",first,200);
        send(post("/api/quotations/"+id+"/revision"),first,Map.of("reason","从旧版继续"),400);
        assertFalse(detail(id,first).path("actions").toString().contains("revise"));
        jdbc.update("UPDATE crm_opportunity SET stage='LOST',close_reason='测试取消' WHERE id=?",business);
        send(post("/api/quotations"),first,body(customer,business,contact),400);action(next,"ACCEPT",first,400);
        assertEquals("SENT",detail(next,first).path("status").asText());
    }

    private void accepted(long id) throws Exception {action(id,"SEND",first,200);action(id,"ACCEPT",first,200);}
    private void action(long id,String action,Actor user,int status) throws Exception {send(post("/api/quotations/"+id+"/transition"),user,Map.of("action",action,"note","测试反馈与确认依据"),status);}
    private JsonNode detail(long id,Actor user) throws Exception{return get("/api/quotations/"+id,user,200);}
    private long create(long client,long opportunity,long person,Actor user) throws Exception{return send(post("/api/quotations"),user,body(client,opportunity,person),200).asLong();}
    private Map<String,Object> item(int quantity,String price){return new HashMap<>(Map.of("skuId",sku,"quantity",quantity,"unitPrice",price,"requirements","测试包装"));}
    private Map<String,Object> body(long client,long opportunity,long person){Map<String,Object> body=new HashMap<>();body.put("customerId",client);body.put("opportunityId",opportunity);body.put("contactId",person);body.put("title","测试吉他报价");body.put("quoteDate",LocalDate.now().toString());body.put("validUntil",LocalDate.now().plusDays(14).toString());body.put("taxMode","INCLUDED");body.put("paymentTerms","按双方确认条件执行");body.put("deliveryTerms","确认订单后约定交期");body.put("freightTerms","另行确认");body.put("remark","事务回滚报价测试");body.put("items",List.of(item(2,"34.56")));return body;}
    private long client(Actor user) throws Exception{return send(post("/api/customers"),user,Map.of("companyName","报价测试客户"+user.id,"customerType","STORE","region","上海","source","测试","contactName","原联系人","phone","00000000000","email","quote@example.com","relationship","POTENTIAL","protectedCustomer",false),200).asLong();}
    private long primary(long id){return jdbc.queryForObject("SELECT id FROM crm_contact WHERE customer_id=? AND is_primary=1",Long.class,id);}
    private long business(long client,Actor user) throws Exception{return send(post("/api/opportunities"),user,Map.of("customerId",client,"title","吉他采购"+code(),"stage","QUALIFIED","remark","测试需求"),200).asLong();}
    private void extra(Actor user,String permission){String code=code();jdbc.update("INSERT INTO sys_role(role_code,role_name,data_scope) VALUES(?,'报价测试角色',1)",code);jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=?",user.id,code);jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code=? WHERE r.role_code=?",permission,code);}
    private Actor actor(String role,String department) throws Exception {String no=code();long dept=jdbc.queryForObject("SELECT id FROM sys_dept WHERE dept_code=?",Long.class,department);jdbc.update("INSERT INTO sys_user(employee_no,real_name,password_hash,dept_id) VALUES(?,'报价测试员工',?,?)",no,encoder.encode("Quote@123456"),dept);long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?",Long.class,no);jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=?",id,role);String token=send(post("/api/auth/login"),null,Map.of("employeeNo",no,"password","Quote@123456"),200).path("token").asText();return new Actor(id,token);}
    private String code(){return "QT"+UUID.randomUUID().toString().replace("-","").substring(0,20);}
    private JsonNode get(String url,Actor user,int status) throws Exception{return send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url),user,null,status);}
    private JsonNode send(MockHttpServletRequestBuilder request,Actor user,Object body,int status) throws Exception {if(user!=null)request.header("Authorization","Bearer "+user.token);if(body!=null)request.contentType("application/json").content(json.writeValueAsString(body));return json.readTree(mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");}
}
