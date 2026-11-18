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
class DeliveryApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired QuotationMapper quotes;
    record Actor(long id,String token) {}
    Actor sales,manager,other,production,warehouse,unassigned;
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
        production=actor("PRODUCTION","DEPT_PRODUCTION");warehouse=actor("SUPPLY_STAFF","DEPT_SUPPLY");unassigned=actor("PRODUCTION","DEPT_PRODUCTION");
        confirm();
    }

    @Test void confirmedOrdersEnterDeliveryAndOperationalUsersOnlySeeAssignedSafeFields() throws Exception {
        assertEquals("PENDING",delivery(manager).path("status").asText());
        assertEquals(0,send(get("/api/deliveries"),production,null,200).path("total").asInt());
        assign();
        var data=delivery(production);
        assertEquals(order,data.path("orderId").asLong());
        assertFalse(data.toString().contains("unitPrice"));assertFalse(data.toString().contains("totalAmount"));assertFalse(data.toString().contains("paymentTerms"));
        send(get("/api/deliveries/"+order),unassigned,null,403);
        send(get("/api/deliveries/"+order),other,null,403);
        send(get("/api/orders/"+order),production,null,403);
        send(put("/api/deliveries/"+order+"/assignment"),sales,assignment(),403);
    }

    @Test void assignmentAndProgressCheckResponsibilitiesVersionsAndKeepHistory() throws Exception {
        var request=assignment();request.put("managerId",sales.id);
        send(put("/api/deliveries/"+order+"/assignment"),manager,request,400);
        assign();
        var progress=progress("PRODUCTION");
        send(put("/api/deliveries/"+order+"/progress"),unassigned,progress,403);
        send(put("/api/deliveries/"+order+"/progress"),production,progress,200);
        send(put("/api/deliveries/"+order+"/progress"),production,progress,400);
        assertEquals("PRODUCTION",delivery(sales).path("status").asText());
        assertTrue(delivery(manager).path("started").asBoolean());
        assertEquals(3,delivery(manager).path("events").size());
        send(put("/api/deliveries/"+order+"/progress"),warehouse,progress("STOCKING"),200);
        assertEquals("STOCKING",delivery(manager).path("preparation").asText());
    }

    @Test void shipmentsArePerLineIdempotentAndCannotExceedRemainingQuantities() throws Exception {
        assign();stock();
        var request=shipment(1);
        long first=send(post("/api/shipments"),warehouse,request,200).asLong();
        assertEquals(first,send(post("/api/shipments"),warehouse,request,200).asLong());
        assertEquals("PARTIAL",delivery(manager).path("status").asText());
        assertEquals(1,delivery(manager).path("items").get(0).path("shippedQuantity").asInt());
        send(post("/api/shipments"),warehouse,shipment(2),400);
        var cross=shipment(1);cross.put("items",List.of(Map.of("orderItemId",9999999,"quantity",1)));
        send(post("/api/shipments"),warehouse,cross,400);
        send(post("/api/shipments"),other,shipment(1),403);
        send(post("/api/shipments"),warehouse,shipment(1),200);
        assertEquals("SHIPPED",delivery(manager).path("status").asText());
        send(post("/api/shipments"),warehouse,shipment(1),400);
        var result=send(get("/api/shipments/"+first),warehouse,null,200);
        assertFalse(result.toString().contains("unitPrice"));assertEquals(1,result.path("items").get(0).path("quantity").asInt());
        send(get("/api/shipments/"+first),other,null,403);
    }

    @Test void fullShipmentRequiresAllProductLinesNotAnOrderTotal() throws Exception {
        jdbc.update("INSERT INTO crm_sales_order_item(order_id,sku_id,sort_no,product_name,model_code,sku_code,unit,specification,requirements,quantity,unit_price,line_amount) SELECT order_id,sku_id,2,'第二产品',model_code,sku_code,unit,specification,requirements,5,unit_price,500 FROM crm_sales_order_item WHERE order_id=?",order);
        assign();stock();
        send(post("/api/shipments"),warehouse,shipment(2),200);
        assertEquals("PARTIAL",delivery(manager).path("status").asText());
        var second=delivery(warehouse).path("items").get(1);
        var request=shipment(1);request.put("items",List.of(Map.of("orderItemId",second.path("id").asLong(),"quantity",5)));
        send(post("/api/shipments"),warehouse,request,200);
        assertEquals("SHIPPED",delivery(manager).path("status").asText());
    }

    @Test void actualExecutionRequiresFreshAcknowledgementsBeforeOrderChanges() throws Exception {
        assign();stock();
        var edit=content(manager);line(edit).put("quantity",4);
        var change=Map.of("content",edit,"reason","客户增加数量","customerConfirmation","客户确认新数量");
        send(post("/api/orders/"+order+"/change"),manager,change,400);
        acknowledge(production,"PRODUCTION");
        send(post("/api/orders/"+order+"/change"),manager,change,400);
        acknowledge(warehouse,"STOCKING");
        long originalLine=delivery(manager).path("items").get(0).path("id").asLong();
        send(post("/api/orders/"+order+"/change"),manager,change,200);
        assertEquals(originalLine,delivery(manager).path("items").get(0).path("id").asLong());
        edit=content(manager);line(edit).put("quantity",5);
        send(post("/api/orders/"+order+"/change"),manager,Map.of("content",edit,"reason","再次变更","customerConfirmation","再次确认"),400);
        send(post("/api/orders/"+order+"/cancel-confirmed"),manager,Map.of("version",detail(manager).path("version").asInt(),"reason","取消"),400);
    }

    @Test void shippedLinesCannotBeRemovedRepricedOrReducedAndCancellationIsBlocked() throws Exception {
        assign();stock();acknowledge(production,"PRODUCTION");acknowledge(warehouse,"STOCKING");
        var edit=content(manager);line(edit).put("quantity",4);
        send(post("/api/orders/"+order+"/change"),manager,Map.of("content",edit,"reason","增加采购","customerConfirmation","客户确认"),200);
        send(post("/api/shipments"),warehouse,shipment(2),200);
        acknowledge(production,"PRODUCTION");acknowledge(warehouse,"STOCKING");
        edit=content(manager);line(edit).put("quantity",1);
        send(post("/api/orders/"+order+"/change"),manager,Map.of("content",edit,"reason","减少采购","customerConfirmation","客户确认"),400);
        edit=content(manager);line(edit).put("unitPrice",new BigDecimal("1.00"));
        send(post("/api/orders/"+order+"/change"),manager,Map.of("content",edit,"reason","改已发单价","customerConfirmation","客户确认"),400);
        send(post("/api/orders/"+order+"/cancel-confirmed"),manager,Map.of("version",detail(manager).path("version").asInt(),"reason","不可整单取消"),400);
        assertEquals(2,delivery(manager).path("items").get(0).path("shippedQuantity").asInt());
    }

    @Test void correctionRetainsOriginalShipmentAndRecalculatesProgress() throws Exception {
        assign();stock();long id=send(post("/api/shipments"),warehouse,shipment(2),200).asLong();
        send(post("/api/shipments/"+id+"/void"),warehouse,Map.of("reason","录入数量错误"),403);
        send(post("/api/shipments/"+id+"/void"),manager,Map.of("reason","录入数量错误"),200);
        assertEquals("STOCKING",delivery(manager).path("status").asText());
        assertEquals("VOID",send(get("/api/shipments/"+id),manager,null,200).path("status").asText());
        assertEquals(1,send(get("/api/shipments/"+id),manager,null,200).path("items").size());
        send(post("/api/shipments/"+id+"/void"),manager,Map.of("reason","重复更正"),400);
    }

    @Test void receiptCompletesOrderAndThenFreezesExecutionAndCorrections() throws Exception {
        assign();stock();
        send(post("/api/deliveries/"+order+"/complete"),sales,Map.of("version",delivery(sales).path("version").asInt(),"note","客户签收"),400);
        long id=send(post("/api/shipments"),warehouse,shipment(2),200).asLong();
        send(post("/api/deliveries/"+order+"/complete"),sales,Map.of("version",delivery(sales).path("version").asInt(),"note","客户已全部签收"),200);
        assertEquals("COMPLETED",detail(manager).path("status").asText());
        assertEquals("COMPLETED",delivery(manager).path("status").asText());
        send(post("/api/shipments/"+id+"/void"),manager,Map.of("reason","禁止修改已完成交付"),400);
        send(post("/api/shipments"),warehouse,shipment(1),400);
    }

    private JsonNode delivery(Actor actor) throws Exception {return send(get("/api/deliveries/"+order),actor,null,200);}
    private Map<String,Object> assignment() throws Exception {return new HashMap<>(Map.of("version",delivery(manager).path("version").asInt(),"managerId",manager.id,"productionId",production.id,"warehouseId",warehouse.id,"expectedDate",LocalDate.now().plusDays(5).toString(),"exceptionNote","测试交付说明"));}
    private void assign() throws Exception {send(put("/api/deliveries/"+order+"/assignment"),manager,assignment(),200);}
    private Map<String,Object> progress(String state) throws Exception {return Map.of("version",delivery(manager).path("version").asInt(),"preparation",state,"expectedDate",LocalDate.now().plusDays(5).toString(),"exceptionNote","测试进度");}
    private void stock() throws Exception {send(put("/api/deliveries/"+order+"/progress"),warehouse,progress("STOCKING"),200);}
    private void acknowledge(Actor actor,String kind) throws Exception {send(post("/api/deliveries/"+order+"/acknowledgement"),actor,Map.of("orderVersion",delivery(actor).path("orderVersion").asInt(),"kind",kind,"note","已核对拟调整内容，确认执行影响"),200);}
    private Map<String,Object> shipment(int quantity) throws Exception {
        var delivery=delivery(warehouse);
        Map<String,Object> request=new HashMap<>();
        request.put("orderId",order);request.put("requestKey",UUID.randomUUID().toString());request.put("orderVersion",delivery.path("orderVersion").asInt());
        request.put("recipientName","测试收货人");request.put("recipientPhone","000");request.put("shippingAddress","测试地址不可发货");
        request.put("carrier","测试物流");request.put("trackingNo","TEST-000");request.put("shippedAt",java.time.LocalDateTime.now().minusMinutes(1).toString());
        request.put("items",List.of(Map.of("orderItemId",delivery.path("items").get(0).path("id").asLong(),"quantity",quantity)));
        return request;
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
