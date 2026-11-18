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

@SpringBootTest @AutoConfigureMockMvc @Transactional
class OrderConstraintApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired QuotationMapper quotes;
    record Actor(long id,String token) {}
    Actor first,second,manager;
    long customer,other,business,quote;
    @BeforeEach void prepare() throws Exception {
        first=actor("SALES_STAFF","DEPT_SALES_EAST");second=actor("SALES_STAFF","DEPT_SALES_EAST");manager=actor("SALES_MANAGER","DEPT_SALES");
        customer=client(first);other=client(second);
        business=send(post("/api/opportunities"),first,Map.of("customerId",customer,"title","订单约束测试商机","stage","QUALIFIED"),200).asLong();quote=quotation(true);
    }

    @Test void generationRequiresAcceptedFinalAndRepeatedRequestsReturnSameOrder() throws Exception {
        jdbc.update("UPDATE crm_quotation SET is_final=0,status='DRAFT' WHERE id=?",quote);generate(first,400);
        jdbc.update("UPDATE crm_quotation SET status='SENT' WHERE id=?",quote);generate(first,400);
        jdbc.update("UPDATE crm_quotation SET status='ACCEPTED' WHERE id=?",quote);generate(first,400);
        jdbc.update("UPDATE crm_quotation SET is_final=1 WHERE id=?",quote);
        var available=send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/quotations/"+quote),first,null,200).path("actions");
        assertTrue(available.toString().contains("\"order\""));
        long id=generate(first,200);assertEquals(id,generate(first,200));
        var order=get(id,first,200);assertEquals(customer,order.path("customerId").asLong());assertEquals(0,new BigDecimal("200.00").compareTo(order.path("totalAmount").decimalValue()));
        assertEquals(2,order.path("sourceSnapshot").path("items").get(0).path("quantity").asInt());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM crm_sales_order WHERE quotation_id=?",Integer.class,quote));
        var linked=send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/quotations/"+quote),first,null,200).path("actions");
        assertTrue(linked.toString().contains("orderView"));assertFalse(linked.toString().contains("void"));
    }

    @Test void activeDraftLocksQuoteFinalSelectionVoidAndRevisionUntilCancelled() throws Exception {
        long id=generate(first,200),alternative=quotation(false);
        action(quote,"VOID",first,400);action(alternative,"FINAL",first,400);
        send(post("/api/quotations/"+quote+"/revision"),first,Map.of("reason","绕过订单修订"),400);
        send(post("/api/orders/"+id+"/cancel"),first,Map.of("reason","客户调整采购"),200);
        assertEquals(id,generate(first,200));assertEquals("CANCELLED",get(id,first,200).path("status").asText());
        action(quote,"VOID",first,200);action(alternative,"FINAL",first,200);
        long next=send(post("/api/orders/from-quotation/"+alternative),first,null,200).asLong();assertNotEquals(id,next);
    }

    @Test void confirmationValidatesSavedInformationAndFreezesOrderAndQuote() throws Exception {
        long id=generate(first,200);send(post("/api/orders/"+id+"/confirm"),first,null,400);
        send(put("/api/orders/"+id),first,draft(),200);
        jdbc.update("UPDATE crm_quotation SET is_final=0 WHERE id=?",quote);send(post("/api/orders/"+id+"/confirm"),first,null,400);
        jdbc.update("UPDATE crm_quotation SET is_final=1 WHERE id=?",quote);send(post("/api/orders/"+id+"/confirm"),first,null,200);
        assertEquals("WON",jdbc.queryForObject("SELECT stage FROM crm_opportunity WHERE id=?",String.class,business));
        assertEquals("CONFIRMED",get(id,first,200).path("status").asText());
        send(put("/api/orders/"+id),first,draft(),400);send(post("/api/orders/"+id+"/cancel"),first,Map.of("reason","直接取消已确认单"),400);
        action(quote,"VOID",first,400);send(post("/api/quotations/"+quote+"/revision"),first,Map.of("reason","修改来源"),400);
    }

    @Test void unfinishedOrderBlocksBothPoolRequestAndApproval() throws Exception {
        long id=generate(first,200);
        var result=mvc.perform(post("/api/customers/"+customer+"/pool-requests").header("Authorization","Bearer "+first.token).contentType("application/json").content("{\"reason\":\"测试申请\"}")).andExpect(status().isBadRequest()).andReturn();
        assertTrue(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("未完成销售订单"));
        jdbc.update("INSERT INTO crm_pool_request(customer_id,requested_by,reason) VALUES(?,?,'生成订单前已提交的测试申请')",customer,first.id);
        long request=jdbc.queryForObject("SELECT id FROM crm_pool_request WHERE customer_id=? AND status='PENDING'",Long.class,customer);
        send(post("/api/customers/"+customer+"/pool-requests/"+request+"/review"),manager,Map.of("approve",true,"note","尝试回收"),400);
        assertEquals("DRAFT",get(id,first,200).path("status").asText());
    }

    @Test void otherSalesCannotGenerateReadOrModifyOrdersAndTransferMovesAccess() throws Exception {
        long id=generate(first,200);generate(second,403);get(id,second,403);
        send(put("/api/orders/"+id),second,draft(),403);send(post("/api/orders/"+id+"/confirm"),second,null,403);send(post("/api/orders/"+id+"/cancel"),second,Map.of("reason","越权取消"),403);
        assertEquals(0,send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/orders"),second,null,200).path("total").asInt());
        send(post("/api/customers/"+customer+"/assign"),manager,Map.of("ownerId",second.id,"reason","经理安排订单交接"),200);
        get(id,first,403);assertEquals(customer,get(id,second,200).path("customerId").asLong());
    }

    @Test void orderSnapshotAndBindingCannotBeChangedThroughDraftRequest() throws Exception {
        long id=generate(first,200);var request=draft();request.put("customerId",other);request.put("quotationId",9999);request.put("totalAmount",1);request.put("status","CONFIRMED");
        send(put("/api/orders/"+id),first,request,200);var order=get(id,first,200);
        assertEquals(customer,order.path("customerId").asLong());assertEquals(quote,order.path("quotationId").asLong());assertEquals("DRAFT",order.path("status").asText());assertEquals(0,new BigDecimal("200.00").compareTo(order.path("totalAmount").decimalValue()));
        jdbc.update("UPDATE crm_quotation SET title='后续报价标题' WHERE id=?",quote);assertEquals("订单约束报价",get(id,first,200).path("sourceSnapshot").path("quotation").path("title").asText());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE crm_sales_order SET customer_id=? WHERE id=?",other,id));
        long alternative=quotation(false);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO crm_sales_order(order_no,quotation_id,customer_id,opportunity_id,created_by,status,total_amount,snapshot_json) SELECT ?,?,customer_id,opportunity_id,created_by,'DRAFT',total_amount,snapshot_json FROM crm_sales_order WHERE id=?",code(),alternative,id));
    }

    @Test void permissionsAreEnforcedSeparatelyFromQuoteAccess() throws Exception {
        long id=generate(first,200);
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code='order:confirm'");send(post("/api/orders/"+id+"/confirm"),first,null,403);
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code='order:add'");generate(first,403);
        assertEquals("ACCEPTED",send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/quotations/"+quote),first,null,200).path("status").asText());
    }

    @Test void completedOrderNoLongerCountsAsUnfinishedForPool() throws Exception {
        long id=generate(first,200);send(put("/api/orders/"+id),first,draft(),200);send(post("/api/orders/"+id+"/confirm"),first,null,200);
        // Future delivery workflow is responsible for completing an order.
        jdbc.update("UPDATE crm_sales_order SET status='COMPLETED' WHERE id=?",id);
        send(post("/api/customers/"+customer+"/pool-requests"),first,Map.of("reason","交付结束后重新安排维护"),200);
        action(quote,"VOID",first,400);
    }

    private long quotation(boolean isFinal){Quotation q=new Quotation();q.setQuoteNo(code());q.setVersionNo(1);q.setCustomerId(customer);q.setOpportunityId(business);q.setContactId(jdbc.queryForObject("SELECT id FROM crm_contact WHERE customer_id=? AND is_primary=1",Long.class,customer));q.setCreatedBy(first.id);q.setTitle("订单约束报价");q.setStatus("ACCEPTED");q.setIsFinal(isFinal);q.setQuoteDate(LocalDate.now());q.setValidUntil(LocalDate.now().plusDays(14));q.setCurrency("CNY");q.setTaxMode("INCLUDED");q.setPaymentTerms("测试付款约定");q.setDeliveryTerms("测试交期约定");q.setFreightTerms("");q.setRemark("");q.setCustomerName("测试客户");q.setContactName("联系人");q.setContactPhone("000");q.setContactLandline("");q.setContactWechat("");q.setContactEmail("");q.setSellerName("测试销售");q.setTotalAmount(new BigDecimal("200.00"));quotes.insert(q);QuotationItem item=new QuotationItem();item.setQuotationId(q.getId());item.setSkuId(jdbc.queryForObject("SELECT MIN(id) FROM crm_product_sku",Long.class));item.setSortNo(1);item.setProductName("测试吉他");item.setModelCode("TEST");item.setSkuCode("TEST-SKU");item.setUnit("把");item.setSpecification("测试配置");item.setRequirements("");item.setQuantity(2);item.setUnitPrice(new BigDecimal("100.00"));item.setLineAmount(new BigDecimal("200.00"));quotes.insertItem(item);return q.getId();}
    private Map<String,Object> draft(){return new HashMap<>(Map.of("customerOrderNo","TEST-PO","recipientName","测试收货人","recipientPhone","00000000000","shippingAddress","测试地址不可发货","promisedDate",LocalDate.now().plusDays(7).toString(),"confirmationNote","客户正式下单确认测试依据","version",0));}
    private long generate(Actor user,int status) throws Exception{return send(post("/api/orders/from-quotation/"+quote),user,null,status).asLong();}
    private JsonNode get(long id,Actor user,int status) throws Exception{return send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/orders/"+id),user,null,status);}
    private void action(long id,String action,Actor user,int status) throws Exception{send(post("/api/quotations/"+id+"/transition"),user,Map.of("action",action,"note","订单约束测试"),status);}
    private long client(Actor user) throws Exception{return send(post("/api/customers"),user,Map.of("companyName","订单约束客户"+user.id,"customerType","STORE","region","上海","source","测试","contactName","测试联系人","phone","000","relationship","POTENTIAL","protectedCustomer",false),200).asLong();}
    private String code(){return "OC"+UUID.randomUUID().toString().replace("-","").substring(0,20);}
    private Actor actor(String role,String department) throws Exception{String no=code();long dept=jdbc.queryForObject("SELECT id FROM sys_dept WHERE dept_code=?",Long.class,department);jdbc.update("INSERT INTO sys_user(employee_no,real_name,password_hash,dept_id) VALUES(?,'订单约束测试员工',?,?)",no,encoder.encode("Order@123456"),dept);long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?",Long.class,no);jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=?",id,role);return new Actor(id,send(post("/api/auth/login"),null,Map.of("employeeNo",no,"password","Order@123456"),200).path("token").asText());}
    private JsonNode send(MockHttpServletRequestBuilder request,Actor user,Object body,int status) throws Exception{if(user!=null)request.header("Authorization","Bearer "+user.token);if(body!=null)request.contentType("application/json").content(json.writeValueAsString(body));return json.readTree(mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");}
}
