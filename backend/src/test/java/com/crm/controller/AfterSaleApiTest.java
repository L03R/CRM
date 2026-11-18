package com.crm.controller;

import com.crm.entity.*;
import com.crm.mapper.QuotationMapper;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AfterSaleApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired QuotationMapper quotes;
    record Actor(long id,String token) {}
    Actor sales,manager,other,helper,warehouse,unassigned;
    long customer,business,quote,order;

    @BeforeEach void prepare() throws Exception {
        sales=actor("SALES_STAFF","DEPT_SALES_EAST");
        manager=actor("SALES_MANAGER","DEPT_SALES");
        other=actor("SALES_STAFF","DEPT_SALES_SOUTH");
        customer=send(post("/api/customers"),sales,Map.of("companyName","订单维护客户"+sales.id,"customerType","STORE","region","上海","source","测试","contactName","测试联系人","phone","000","relationship","POTENTIAL","protectedCustomer",false),200).asLong();
        business=send(post("/api/opportunities"),sales,Map.of("customerId",customer,"title","订单维护商机","stage","QUALIFIED"),200).asLong();
        Quotation q=new Quotation();
        q.setQuoteNo(code());q.setVersionNo(1);q.setCustomerId(customer);q.setOpportunityId(business);
        q.setContactId(jdbc.queryForObject("SELECT id FROM crm_contact WHERE customer_id=? AND is_primary=1",Long.class,customer));
        q.setCreatedBy(sales.id);q.setTitle("订单维护报价");q.setStatus("ACCEPTED");q.setIsFinal(true);
        q.setQuoteDate(LocalDate.now());q.setValidUntil(LocalDate.now().plusDays(14));q.setCurrency("CNY");q.setTaxMode("INCLUDED");
        q.setPaymentTerms("测试付款条件");q.setDeliveryTerms("测试交付条件");q.setFreightTerms("");q.setRemark("");
        q.setCustomerName("测试客户");q.setContactName("联系人");q.setContactPhone("000");q.setContactLandline("");q.setContactWechat("");q.setContactEmail("");q.setSellerName("测试销售");q.setTotalAmount(new BigDecimal("200.00"));
        quotes.insert(q);quote=q.getId();
        QuotationItem item=new QuotationItem();item.setQuotationId(quote);item.setSkuId(jdbc.queryForObject("SELECT MIN(id) FROM crm_product_sku",Long.class));
        item.setSortNo(1);item.setProductName("原始吉他快照");item.setModelCode("TEST");item.setSkuCode("TEST-SKU");item.setUnit("把");item.setSpecification("原始配置");item.setRequirements("");item.setQuantity(2);item.setUnitPrice(new BigDecimal("100.00"));item.setLineAmount(new BigDecimal("200.00"));quotes.insertItem(item);
        order=send(post("/api/orders/from-quotation/"+quote),sales,null,200).asLong();
        helper=actor("PRODUCTION","DEPT_PRODUCTION");warehouse=actor("SUPPLY_STAFF","DEPT_SUPPLY");unassigned=actor("PRODUCTION","DEPT_PRODUCTION");
        confirm();
    }

    @Test void unknownOrderCanBeRegisteredWithoutInventingASourceOrChangingBusinessState() throws Exception {
        var body=body(false);body.put("ownerId",other.id);body.put("status","CLOSED");
        long id=create(body);var record=caseDetail(id,sales);
        assertTrue(record.path("sourcePending").asBoolean());assertTrue(record.path("orderId").isNull());
        assertEquals("PENDING",record.path("status").asText());assertEquals(sales.id,record.path("ownerId").asLong());
        assertEquals("未确认型号吉他",record.path("items").get(0).path("productName").asText());
        assertEquals("CONFIRMED",detail(sales).path("status").asText());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM crm_shipment WHERE order_id=?",Integer.class,order));
        var consultation=body(false);consultation.put("issueType","CONSULTATION");
        assertFalse(caseDetail(create(consultation),sales).path("sourcePending").asBoolean());
        send(get("/api/after-sales/"+id),other,null,403);
    }

    @Test void sourceValidationAndSnapshotsUseTheSelectedCustomerOrderAndShipment() throws Exception {
        long shipment=ship();var body=body(true);source(body).put("shipmentId",shipment);
        long id=create(body);var record=caseDetail(id,sales);
        assertFalse(record.path("sourcePending").asBoolean());assertEquals(shipment,record.path("shipmentId").asLong());
        assertEquals("原始吉他快照",record.path("items").get(0).path("productName").asText());
        assertFalse(record.toString().contains("unitPrice"));assertFalse(record.toString().contains("paymentTerms"));
        // 同一产品可以再次维修，不按历史售后数量累计拦截。
        create(body);
        var wrong=body(true);wrong.put("customerId",send(post("/api/customers"),sales,Map.of("companyName",code(),"customerType","STORE","region","测试","source","测试","contactName","测试","phone","000","relationship","POTENTIAL","protectedCustomer",false),200).asLong());
        send(post("/api/after-sales"),sales,wrong,400);
        wrong=body(true);source(wrong).put("items",List.of(Map.of("orderItemId",999999,"quantity",1)));
        send(post("/api/after-sales"),sales,wrong,400);
        wrong=body(true);source(wrong).put("items",List.of(Map.of("orderItemId",detail(sales).path("items").get(0).path("id").asLong(),"quantity",3)));
        send(post("/api/after-sales"),sales,wrong,400);
        wrong=body(false);source(wrong).put("shipmentId",shipment);send(post("/api/after-sales"),sales,wrong,400);
    }

    @Test void assignedHelperCanOnlyReadAndAppendAssistanceForTheirCases() throws Exception {
        long id=create(body(true));
        send(get("/api/after-sales/"+id),helper,null,403);
        assertEquals(0,send(get("/api/after-sales"),helper,null,200).path("total").asInt());
        transfer(id,helper.id);
        var record=caseDetail(id,helper);
        assertTrue(record.path("actions").toString().contains("assist"));assertFalse(record.toString().contains("totalAmount"));
        send(get("/api/after-sales/"+id),unassigned,null,403);
        send(post("/api/after-sales/"+id+"/assist"),helper,Map.of("version",record.path("version").asInt(),"content","初步核实需技术指导","occurredAt",java.time.LocalDateTime.now().minusMinutes(1).toString()),200);
        send(post("/api/after-sales/"+id+"/follow"),helper,follow(id,"不能修改销售跟进计划"),403);
        send(post("/api/after-sales/"+id+"/close"),helper,closeBody(id),403);
        send(get("/api/orders/"+order),helper,null,403);
        assertEquals("ASSIST",caseDetail(id,sales).path("events").get(0).path("action").asText());
    }

    @Test void externalHandoffNeedsNoCrmAccountAndDoesNotMeanResolved() throws Exception {
        long id=create(body(false));transfer(id,null);
        var record=caseDetail(id,sales);
        assertEquals("TRANSFERRED",record.path("status").asText());assertEquals("外部维修点张师傅",record.path("handlerName").asText());
        assertEquals("",record.path("resultNote").asText());assertTrue(record.path("sourcePending").asBoolean());
        assertEquals("CONFIRMED",detail(sales).path("status").asText());
    }

    @Test void feedbackReminderAppearsInTasksAgendaAndOverviewThenDisappearsWhenClosed() throws Exception {
        var body=body(true);body.put("nextFollowAt",java.time.LocalDateTime.now().minusHours(1).toString());long id=create(body);
        var tasks=send(get("/api/dashboard/tasks").param("type","AFTERSALE_FOLLOW").param("due","OVERDUE"),sales,null,200);
        assertEquals(1,tasks.path("total").asInt());assertEquals("AFTERSALE",tasks.path("records").get(0).path("kind").asText());
        assertEquals(id,tasks.path("records").get(0).path("id").asLong());
        assertEquals(0,send(get("/api/dashboard/tasks").param("type","AFTERSALE_FOLLOW"),other,null,200).path("total").asInt());
        var day=LocalDate.now().toString();
        assertEquals(1,send(get("/api/dashboard/agenda").param("start",day).param("end",day),sales,null,200).size());
        assertTrue(send(get("/api/dashboard/overview"),sales,null,200).path("todos").toString().contains("AFTERSALE"));
        send(post("/api/after-sales/"+id+"/close"),sales,closeBody(id),200);
        assertEquals(0,send(get("/api/dashboard/tasks").param("type","AFTERSALE_FOLLOW"),sales,null,200).path("total").asInt());
        assertTrue(caseDetail(id,sales).path("nextFollowAt").isNull());
        assertEquals("CONFIRMED",detail(sales).path("status").asText());
    }

    @Test void closedCaseIsFrozenAndOnlyManagerCanReopenWhileRetainingHistory() throws Exception {
        long id=create(body(true));send(post("/api/after-sales/"+id+"/close"),sales,closeBody(id),200);
        send(post("/api/after-sales/"+id+"/follow"),sales,follow(id,"不可直接覆盖结案"),400);
        var reopen=Map.of("version",caseDetail(id,sales).path("version").asInt(),"note","客户补充新情况","nextFollowAt",java.time.LocalDateTime.now().plusDays(1).toString());
        send(post("/api/after-sales/"+id+"/reopen"),sales,reopen,403);
        send(post("/api/after-sales/"+id+"/reopen"),manager,reopen,200);
        var record=caseDetail(id,sales);assertEquals("PENDING",record.path("status").asText());assertTrue(record.path("events").toString().contains("CLOSE"));
        assertEquals(1,send(get("/api/dashboard/tasks").param("type","AFTERSALE_FOLLOW"),sales,null,200).path("total").asInt());
        assertTrue(record.path("closedAt").isNull());
    }

    @Test void sourceCanBeVerifiedLaterAndStaleEditsCannotOverwriteIt() throws Exception {
        long id=create(body(false));var before=caseDetail(id,sales);var change=Map.of("version",before.path("version").asInt(),"source",source(body(true)),"note","已核实来自本次采购订单");
        send(put("/api/after-sales/"+id+"/source"),sales,change,200);
        send(put("/api/after-sales/"+id+"/source"),sales,change,400);
        assertEquals(order,caseDetail(id,sales).path("orderId").asLong());assertFalse(caseDetail(id,sales).path("sourcePending").asBoolean());
        var record=caseDetail(id,sales);assertEquals("SOURCE",record.path("events").get(0).path("action").asText());
        assertTrue(record.path("events").get(0).path("content").asText().contains("未确认型号吉他"));
        var options=send(get("/api/after-sales/sources").param("customerId",Long.toString(customer)).param("caseId",Long.toString(id)).param("orderId",Long.toString(order)),sales,null,200);
        assertFalse(options.toString().contains("unitPrice"));
        send(get("/api/after-sales/sources").param("customerId",Long.toString(customer)),other,null,403);
    }

    @Test void managerCanAssignAFollowOwnerButOperationsUseTheirOwnDataScope() throws Exception {
        long id=create(body(true));
        jdbc.update("UPDATE sys_role SET data_scope=4 WHERE role_code='SALES_MANAGER'");
        send(post("/api/after-sales/"+id+"/owner"),manager,Map.of("version",0,"ownerId",other.id,"note","范围不允许"),403);
        jdbc.update("UPDATE sys_role SET data_scope=2 WHERE role_code='SALES_MANAGER'");
        send(post("/api/after-sales/"+id+"/owner"),manager,Map.of("version",0,"ownerId",other.id,"note","指定跨区域售后对接"),200);
        assertEquals(other.id,caseDetail(id,other).path("ownerId").asLong());
        var people=send(get("/api/after-sales/owners"),other,null,200);assertEquals(1,people.size());
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code='aftersale:close'");
        send(post("/api/after-sales/"+id+"/close"),other,closeBody(id),403);
    }

    @Test void imageEvidenceRequiresCaseAccessAndRemainsPrivate() throws Exception {
        long id=create(body(true));byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jF9sAAAAASUVORK5CYII=");
        var file=new org.springframework.mock.web.MockMultipartFile("file","问题.png","image/png",png);
        var response=mvc.perform(multipart("/api/after-sales/"+id+"/images").file(file).header("Authorization","Bearer "+sales.token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long image=json.readTree(response).path("data").asLong();
        mvc.perform(get("/api/after-sales/"+id+"/images/"+image).header("Authorization","Bearer "+sales.token)).andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(png));
        mvc.perform(get("/api/after-sales/"+id+"/images/"+image).header("Authorization","Bearer "+other.token)).andExpect(status().isForbidden());
        assertFalse(caseDetail(id,sales).toString().contains("imageData"));
        var fake=new org.springframework.mock.web.MockMultipartFile("file","伪造.png","image/png","<script>bad</script>".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/after-sales/"+id+"/images").file(fake).header("Authorization","Bearer "+sales.token)).andExpect(status().isBadRequest());
        transfer(id,helper.id);
        mvc.perform(multipart("/api/after-sales/"+id+"/images").file(file).header("Authorization","Bearer "+helper.token)).andExpect(status().isOk());
    }

    @Test void multipleAfterSaleCasesDoNotModifyOriginalOrderOrShipmentQuantities() throws Exception {
        long shipment=ship();var data=body(true);source(data).put("shipmentId",shipment);
        long first=create(data),second=create(data);
        send(post("/api/after-sales/"+first+"/close"),sales,closeBody(first),200);
        assertEquals(2,jdbc.queryForObject("SELECT quantity FROM crm_shipment_item WHERE shipment_id=?",Integer.class,shipment));
        assertEquals(2,jdbc.queryForObject("SELECT quantity FROM crm_sales_order_item WHERE order_id=? AND active=1",Integer.class,order));
        assertEquals("CONFIRMED",detail(sales).path("status").asText());
        assertEquals("PENDING",caseDetail(second,sales).path("status").asText());
    }

    @Test void sourceVerificationKeepsHistoricalShipmentAndProductSnapshotsAfterCorrection() throws Exception {
        long shipment=ship();var body=body(true);source(body).put("shipmentId",shipment);long id=create(body);
        send(post("/api/shipments/"+shipment+"/void"),manager,Map.of("reason","登记错误已更正"),200);
        jdbc.update("UPDATE crm_sales_order_item SET active=0 WHERE order_id=?",order);
        var options=send(get("/api/after-sales/sources").param("customerId",Long.toString(customer)).param("caseId",Long.toString(id)).param("orderId",Long.toString(order)).param("shipmentId",Long.toString(shipment)),sales,null,200);
        assertEquals(1,options.path("lines").size());
        var update=new HashMap<>(source(body));update.put("serialNo","核实后的序列号");
        send(put("/api/after-sales/"+id+"/source"),sales,Map.of("version",caseDetail(id,sales).path("version").asInt(),"source",update,"note","仅补充序列号，保留历史来源"),200);
        assertEquals("原始吉他快照",caseDetail(id,sales).path("items").get(0).path("productName").asText());
        var another=new HashMap<>(body);source(another).put("shipmentId",shipment);
        send(post("/api/after-sales"),sales,another,400);
    }

    private JsonNode caseDetail(long id,Actor actor) throws Exception {return send(get("/api/after-sales/"+id),actor,null,200);}
    private long create(Map<String,Object> body) throws Exception {return send(post("/api/after-sales"),sales,body,200).asLong();}
    private Map<String,Object> body(boolean known) throws Exception {
        Map<String,Object> source=new HashMap<>();source.put("items",known?List.of(Map.of("orderItemId",detail(sales).path("items").get(0).path("id").asLong(),"productName","前端不能覆盖名称","quantity",2)):List.of(Map.of("productName","未确认型号吉他","quantity",1)));
        if(known)source.put("orderId",order);
        Map<String,Object> data=new HashMap<>(Map.of("customerId",customer,"title","吉他客户反馈"+sales.id,"issueType","QUALITY","description","客户反馈开裂，原因尚待核实","expectation","希望得到处理","reporterName","测试反馈人","reporterContact","000000","reportedAt",java.time.LocalDateTime.now().minusMinutes(2).toString(),"nextFollowAt",java.time.LocalDateTime.now().plusDays(1).toString(),"source",source));return data;
    }
    @SuppressWarnings("unchecked") private Map<String,Object> source(Map<String,Object> body) {return (Map<String,Object>)body.get("source");}
    private Map<String,Object> follow(long id,String content) throws Exception {return Map.of("version",caseDetail(id,sales).path("version").asInt(),"content",content,"occurredAt",java.time.LocalDateTime.now().minusMinutes(1).toString(),"nextFollowAt",java.time.LocalDateTime.now().plusDays(1).toString());}
    private Map<String,Object> closeBody(long id) throws Exception {return Map.of("version",caseDetail(id,sales).path("version").asInt(),"resolutionType","OTHER","resultNote","已由其他部门完成处理","customerFeedback","客户认可处理结果","externalNo","TEST-EXTERNAL");}
    private void transfer(long id,Long helperId) throws Exception {
        Map<String,Object> data=new HashMap<>(Map.of("version",caseDetail(id,sales).path("version").asInt(),"handlerName","外部维修点张师傅","handlerContact","电话000","note","已交接问题及来源","nextFollowAt",java.time.LocalDateTime.now().plusDays(1).toString()));
        if(helperId!=null)data.put("helperId",helperId);send(post("/api/after-sales/"+id+"/transfer"),sales,data,200);
    }
    private long ship() throws Exception {
        var d=send(get("/api/deliveries/"+order),manager,null,200);
        send(put("/api/deliveries/"+order+"/assignment"),manager,Map.of("version",d.path("version").asInt(),"managerId",manager.id,"warehouseId",warehouse.id),200);
        d=send(get("/api/deliveries/"+order),warehouse,null,200);
        send(put("/api/deliveries/"+order+"/progress"),warehouse,Map.of("version",d.path("version").asInt(),"preparation","STOCKING"),200);
        d=send(get("/api/deliveries/"+order),warehouse,null,200);
        Map<String,Object> request=new HashMap<>(Map.of("orderId",order,"requestKey",UUID.randomUUID().toString(),"orderVersion",d.path("orderVersion").asInt(),"recipientName","测试收货人","recipientPhone","000","shippingAddress","测试地址","carrier","测试物流","trackingNo","TEST","shippedAt",java.time.LocalDateTime.now().minusMinutes(1).toString(),"items",List.of(Map.of("orderItemId",d.path("items").get(0).path("id").asLong(),"quantity",2))));
        return send(post("/api/shipments"),warehouse,request,200).asLong();
    }

    private void confirm() throws Exception {
        send(put("/api/orders/"+order),sales,content(sales),200);
        send(post("/api/orders/"+order+"/confirm"),sales,null,200);
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> content(Actor user) throws Exception {
        Map<String,Object> content=json.convertValue(detail(user),Map.class);
        content.put("recipientName","测试收货人");content.put("recipientPhone","00000000000");content.put("shippingAddress","测试地址不可发货");
        content.put("promisedDate",LocalDate.now().plusDays(7).toString());content.put("confirmationNote","客户正式采购确认依据");
        return content;
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> line(Map<String,Object> content) {return ((List<Map<String,Object>>)content.get("items")).get(0);}
    private JsonNode detail(Actor user) throws Exception {return send(get("/api/orders/"+order),user,null,200);}
    private void amount(String expected,JsonNode value) {assertEquals(0,new BigDecimal(expected).compareTo(value.decimalValue()));}
    private String code() {return "OM"+UUID.randomUUID().toString().replace("-","").substring(0,20);}
    private Actor actor(String role,String dept) throws Exception {
        String no=code();long deptId=jdbc.queryForObject("SELECT id FROM sys_dept WHERE dept_code=?",Long.class,dept);
        jdbc.update("INSERT INTO sys_user(employee_no,real_name,password_hash,dept_id) VALUES(?,'订单维护测试员工',?,?)",no,encoder.encode("Order@123456"),deptId);
        long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?",Long.class,no);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=?",id,role);
        String token=send(post("/api/auth/login"),null,Map.of("employeeNo",no,"password","Order@123456"),200).path("token").asText();
        return new Actor(id,token);
    }
    private JsonNode send(MockHttpServletRequestBuilder request,Actor user,Object body,int status) throws Exception {
        if(user!=null) request.header("Authorization","Bearer "+user.token);
        if(body!=null) request.contentType("application/json").content(json.writeValueAsString(body));
        return json.readTree(mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }
}
