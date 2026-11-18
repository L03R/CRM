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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CrmAcceptanceApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    record Actor(long id,String token) {}
    Actor market,manager,east,south,production,warehouse;
    long eastDept;

    @BeforeEach void prepare() throws Exception {
        eastDept=dept("DEPT_SALES_EAST");
        production=actor("PRODUCTION",dept("DEPT_PRODUCTION"));warehouse=actor("SUPPLY_STAFF",dept("DEPT_SUPPLY"));
        market=actor("MARKETING",dept("DEPT_MARKETING"));manager=actor("SALES_MANAGER",dept("DEPT_SALES"));
        east=actor("SALES_STAFF",eastDept);south=actor("SALES_STAFF",dept("DEPT_SALES_SOUTH"));
    }
    @Test void leadToShipmentReceiptAndAfterSaleWorksAsOneBusinessChain() throws Exception {
        long lead=lead(market,code());
        send(post("/api/leads/"+lead+"/assign"),manager,Map.of("ownerId",east.id,"reason","验收：按区域分配"),200);
        follow("leads",lead,east);
        long customer=send(post("/api/leads/"+lead+"/convert"),east,null,200).asLong();
        assertEquals(customer,send(post("/api/leads/"+lead+"/convert"),east,null,200).asLong());
        long business=send(post("/api/opportunities"),east,opportunity(customer),200).asLong();
        var contacts=read("/api/quotations/contacts?customerId="+customer,east);
        var skus=read("/api/quotations/skus",east);
        assertTrue(contacts.size()>0);assertTrue(skus.size()>0);
        Map<String,Object> quote=new HashMap<>();
        quote.put("customerId",customer);quote.put("opportunityId",business);quote.put("contactId",contacts.get(0).path("id").asLong());
        quote.put("title","验收采购报价");quote.put("quoteDate",java.time.LocalDate.now().toString());quote.put("validUntil",java.time.LocalDate.now().plusDays(14).toString());
        quote.put("taxMode","INCLUDED");quote.put("paymentTerms","验收付款条件");quote.put("deliveryTerms","验收交期条件");
        quote.put("items",List.of(Map.of("skuId",skus.get(0).path("id").asLong(),"quantity",5,"unitPrice",1000,"requirements","验收标准包装")));
        long quotation=send(post("/api/quotations"),east,quote,200).asLong();
        for(String action:List.of("SEND","ACCEPT","FINAL"))send(post("/api/quotations/"+quotation+"/transition"),east,Map.of("action",action,"note","验收：记录客户沟通确认"),200);
        long order=send(post("/api/orders/from-quotation/"+quotation),east,null,200).asLong();
        var orderData=read("/api/orders/"+order,east);
        Map<String,Object> shipping=new HashMap<>(Map.of("version",orderData.path("version").asInt(),"recipientName","验收收货人","recipientPhone","00000000000","shippingAddress","验收地址，不可实际发货","promisedDate",java.time.LocalDate.now().plusDays(7).toString(),"confirmationNote","验收客户正式采购确认"));
        send(put("/api/orders/"+order),east,shipping,200);
        send(post("/api/orders/"+order+"/confirm"),east,null,200);
        assertEquals("WON",jdbc.queryForObject("SELECT stage FROM crm_opportunity WHERE id=?",String.class,business));
        var delivery=read("/api/deliveries/"+order,manager);
        send(put("/api/deliveries/"+order+"/assignment"),manager,Map.of("version",delivery.path("version").asInt(),"managerId",manager.id,"productionId",production.id,"warehouseId",warehouse.id),200);
        delivery=read("/api/deliveries/"+order,production);
        send(put("/api/deliveries/"+order+"/progress"),production,Map.of("version",delivery.path("version").asInt(),"preparation","PRODUCTION"),200);
        delivery=read("/api/deliveries/"+order,warehouse);
        send(put("/api/deliveries/"+order+"/progress"),warehouse,Map.of("version",delivery.path("version").asInt(),"preparation","STOCKING"),200);
        long firstShipment=ship(order,2);
        assertEquals("PARTIAL",read("/api/deliveries/"+order,east).path("status").asText());
        ship(order,3);
        assertEquals("SHIPPED",read("/api/deliveries/"+order,east).path("status").asText());
        Map<String,Object> issue=new HashMap<>(Map.of("customerId",customer,"title","验收：收到商品后反馈问题","issueType","QUALITY","description","验收反馈，仅验证业务流程","reporterName","验收反馈人","reporterContact","00000000000","reportedAt",LocalDateTime.now().minusMinutes(1).toString(),"source",Map.of("orderId",order,"shipmentId",firstShipment,"items",List.of(Map.of("orderItemId",read("/api/orders/"+order,east).path("items").get(0).path("id").asLong(),"quantity",1)))));
        long afterSale=send(post("/api/after-sales"),east,issue,200).asLong();
        send(post("/api/after-sales/"+afterSale+"/transfer"),east,Map.of("version",0,"handlerName","验收售后协助人","handlerContact","企业内部沟通","helperId",production.id,"note","验收交接，生产协助核实","nextFollowAt",LocalDateTime.now().plusDays(1).toString()),200);
        var record=read("/api/after-sales/"+afterSale,production);
        assertFalse(record.toString().contains("unitPrice"));
        send(post("/api/after-sales/"+afterSale+"/assist"),production,Map.of("version",record.path("version").asInt(),"content","验收：已反馈处理建议","occurredAt",LocalDateTime.now().minusSeconds(1).toString()),200);
        record=read("/api/after-sales/"+afterSale,east);
        send(post("/api/after-sales/"+afterSale+"/follow"),east,Map.of("version",record.path("version").asInt(),"content","验收：已收到反馈，继续对接客户","occurredAt",LocalDateTime.now().minusSeconds(1).toString(),"nextFollowAt",LocalDateTime.now().minusMinutes(5).toString()),200);
        var tasks=read("/api/dashboard/tasks?type=AFTERSALE_FOLLOW&due=OVERDUE",east);
        assertTrue(tasks.path("records").toString().contains("AFTERSALE"));
        expectGet("/api/orders/"+order,south,403);expectGet("/api/quotations/"+quotation,south,403);expectGet("/api/after-sales/"+afterSale,south,403);
        send(post("/api/quotations/"+quotation+"/transition"),east,Map.of("action","VOID","note","不能作废已经交付的来源报价"),400);
        delivery=read("/api/deliveries/"+order,east);
        send(post("/api/deliveries/"+order+"/complete"),east,Map.of("version",delivery.path("version").asInt(),"note","验收：客户确认收到全部商品"),200);
        record=read("/api/after-sales/"+afterSale,east);
        send(post("/api/after-sales/"+afterSale+"/close"),east,Map.of("version",record.path("version").asInt(),"resolutionType","GUIDE","resultNote","验收：相关部门已提供指导","customerFeedback","验收：客户认可处理结果"),200);
        assertEquals("COMPLETED",read("/api/orders/"+order,east).path("status").asText());
        assertEquals("CLOSED",read("/api/after-sales/"+afterSale,east).path("status").asText());
        assertEquals(5,jdbc.queryForObject("SELECT SUM(i.quantity) FROM crm_shipment_item i JOIN crm_shipment h ON h.id=i.shipment_id WHERE h.order_id=? AND h.status='POSTED'",Integer.class,order));
        assertEquals(0,read("/api/dashboard/tasks?type=AFTERSALE_FOLLOW",east).path("total").asInt());
    }

    private long ship(long order,int quantity) throws Exception {
        var delivery=read("/api/deliveries/"+order,warehouse);
        Map<String,Object> request=new HashMap<>(Map.of("orderId",order,"orderVersion",delivery.path("orderVersion").asInt(),"requestKey",UUID.randomUUID().toString(),"recipientName","验收收货人","recipientPhone","000","shippingAddress","验收地址","carrier","验收物流","trackingNo","TEST-ONLY","shippedAt",LocalDateTime.now().minusSeconds(1).toString(),"items",List.of(Map.of("orderItemId",delivery.path("items").get(0).path("id").asLong(),"quantity",quantity))));
        return send(post("/api/shipments"),warehouse,request,200).asLong();
    }

    private Actor actor(String role,long department) throws Exception {
        String no=code();jdbc.update("INSERT INTO sys_user(employee_no,real_name,password_hash,dept_id) VALUES(?,?,?,?)",no,"流程测试员工",encoder.encode("Workflow@123456"),department);
        long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?",Long.class,no);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=?",id,role);
        String token=send(post("/api/auth/login"),null,Map.of("employeeNo",no,"password","Workflow@123456"),200).path("token").asText();
        return new Actor(id,token);
    }
    private long dept(String code){return jdbc.queryForObject("SELECT id FROM sys_dept WHERE dept_code=?",Long.class,code);}
    private long lead(Actor actor,String name) throws Exception{return send(post("/api/leads"),actor,leadBody(name),200).asLong();}
    private Map<String,Object> leadBody(String name){Map<String,Object> body=base(name);body.put("demand","拟采购吉他，待沟通确认");body.put("targetDeptId",eastDept);return body;}
    private Map<String,Object> base(String name){return new HashMap<>(Map.of("companyName",name,"customerType","STORE","region","测试地区","source","测试展会","contactName","测试联系人","phone","00000000001","email","workflow@example.com"));}
    private Map<String,Object> followBody(){return new HashMap<>(Map.of("method","PHONE","content","确认客户身份和采购方向，下次继续沟通","occurredAt",LocalDateTime.now().minusMinutes(1).toString(),"nextFollowAt",LocalDateTime.now().plusDays(1).toString()));}
    private void follow(String resource,long id,Actor actor) throws Exception{send(post("/api/"+resource+"/"+id+"/follows"),actor,followBody(),200);}
    private Map<String,Object> opportunity(long id){return new HashMap<>(Map.of("customerId",id,"title","测试吉他采购意向","expectedAmount",12000,"stage","INITIAL","remark","仅集成测试"));}
    private JsonNode read(String path,Actor actor) throws Exception{return send(get(path),actor,null,200);}
    private void expectGet(String path,Actor actor,int expected) throws Exception{send(get(path),actor,null,expected);}
    private JsonNode send(MockHttpServletRequestBuilder request,Actor actor,Object body,int expected) throws Exception{
        if(actor!=null)request.header("Authorization","Bearer "+actor.token);
        if(body!=null)request.contentType("application/json").content(json.writeValueAsString(body));
        String response=mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(response).path("data");
    }
    private String code(){return "WF"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase();}
}
