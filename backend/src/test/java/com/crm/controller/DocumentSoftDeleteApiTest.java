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
class DocumentSoftDeleteApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired QuotationMapper quotes;
    record Actor(long id,String token) {}
    Actor sales,manager,other;
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
    }

    @Test void deletedQuoteDraftDisappearsFromAllNormalEntriesButRetainsDetailsAndAudit() throws Exception {
        long id=draftQuote();String no=quotes.find(id).getQuoteNo();
        send(delete("/api/quotations/"+id),sales,null,200);
        assertEquals(1,jdbc.queryForObject("SELECT deleted FROM crm_quotation WHERE id=?",Integer.class,id));
        assertEquals(sales.id,jdbc.queryForObject("SELECT deleted_by FROM crm_quotation WHERE id=?",Long.class,id));
        assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM crm_quotation WHERE id=?",java.sql.Timestamp.class,id));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM crm_quotation_item WHERE quotation_id=?",Integer.class,id));
        assertEquals("DELETE",jdbc.queryForObject("SELECT action FROM crm_quotation_event WHERE quotation_id=? ORDER BY id DESC LIMIT 1",String.class,id));
        assertEquals(0,send(get("/api/quotations").param("keyword",no),sales,null,200).path("total").asInt());
        var customerQuotes=send(get("/api/quotations").param("customerId",Long.toString(customer)).param("opportunityId",Long.toString(business)),sales,null,200);
        for(var row:customerQuotes.path("records")) assertNotEquals(id,row.path("id").asLong());
        send(get("/api/quotations/"+id),sales,null,400);
        send(post("/api/quotations/"+id+"/transition"),sales,Map.of("action","SEND","note","不能发送已删除草稿"),400);
        send(delete("/api/quotations/"+id),sales,null,400);
    }

    @Test void deletingOrderDraftAllowsSafeRegenerationFromSameFinalQuote() throws Exception {
        long line=detail(sales).path("items").get(0).path("id").asLong();
        send(delete("/api/orders/"+order),sales,null,200);
        assertEquals(1,jdbc.queryForObject("SELECT deleted FROM crm_sales_order WHERE id=?",Integer.class,order));
        assertEquals(sales.id,jdbc.queryForObject("SELECT deleted_by FROM crm_sales_order WHERE id=?",Long.class,order));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM crm_sales_order_item WHERE id=?",Integer.class,line));
        var event=jdbc.queryForObject("SELECT after_json FROM crm_sales_order_event WHERE order_id=? AND action='DELETE'",String.class,order);
        assertTrue(json.readTree(event).path("order").path("deleted").asBoolean());
        assertEquals(0,send(get("/api/orders").param("keyword","订单维护客户"+sales.id),sales,null,200).path("total").asInt());
        send(get("/api/orders/"+order),sales,null,400);
        send(post("/api/orders/"+order+"/confirm"),sales,null,400);
        var q=send(get("/api/quotations/"+quote),sales,null,200);
        assertTrue(q.path("orderId").isNull());assertTrue(q.path("actions").toString().contains("\"order\""));
        long next=send(post("/api/orders/from-quotation/"+quote),sales,null,200).asLong();
        assertNotEquals(order,next);assertEquals(next,send(post("/api/orders/from-quotation/"+quote),sales,null,200).asLong());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM crm_sales_order WHERE quotation_id=?",Integer.class,quote));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM crm_sales_order WHERE quotation_id=? AND deleted=0",Integer.class,quote));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE crm_sales_order SET deleted=0 WHERE id=?",order));
    }

    @Test void deletingRevisionDraftDoesNotReuseVersionNumbersAndPreviousVersionCanReviseAgain() throws Exception {
        long source=draftQuote();jdbc.update("UPDATE crm_quotation SET status='SENT' WHERE id=?",source);
        long second=send(post("/api/quotations/"+source+"/revision"),sales,Map.of("reason","第二版草稿"),200).asLong();
        send(delete("/api/quotations/"+second),sales,null,200);
        var first=send(get("/api/quotations/"+source),sales,null,200);
        assertEquals(1,first.path("versions").size());assertTrue(first.path("actions").toString().contains("revise"));
        long third=send(post("/api/quotations/"+source+"/revision"),sales,Map.of("reason","删除草稿后重新修订"),200).asLong();
        assertEquals(3,send(get("/api/quotations/"+third),sales,null,200).path("versionNo").asInt());
        assertEquals(third,send(post("/api/quotations/"+source+"/revision"),sales,Map.of("reason","重复提交"),200).asLong());
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM crm_quotation WHERE quote_no=(SELECT quote_no FROM crm_quotation WHERE id=?)",Integer.class,source));
    }

    @Test void onlyUnreferencedDraftsCanBeDeletedAndFormalBusinessRemainsIntact() throws Exception {
        long id=draftQuote();
        for(String state:List.of("SENT","ACCEPTED","REJECTED")) {
            jdbc.update("UPDATE crm_quotation SET status=? WHERE id=?",state,id);
            send(delete("/api/quotations/"+id),sales,null,400);
        }
        send(delete("/api/quotations/"+quote),sales,null,400);
        confirm();
        send(delete("/api/orders/"+order),manager,null,400);
        jdbc.update("UPDATE crm_sales_order SET status='COMPLETED' WHERE id=?",order);
        send(delete("/api/orders/"+order),manager,null,400);
        assertEquals(0,jdbc.queryForObject("SELECT deleted FROM crm_sales_order WHERE id=?",Integer.class,order));
        // 即使状态被错误回写为草稿，已有交付记录也会拦截删除。
        jdbc.update("UPDATE crm_sales_order SET status='DRAFT' WHERE id=?",order);
        send(delete("/api/orders/"+order),manager,null,400);
    }

    @Test void permissionsAndOperationDataScopesProtectBothDeleteEndpoints() throws Exception {
        long id=draftQuote();
        send(delete("/api/quotations/"+id),other,null,403);
        send(delete("/api/orders/"+order),other,null,403);
        jdbc.update("UPDATE sys_role SET data_scope=4 WHERE role_code='SALES_MANAGER'");
        send(delete("/api/quotations/"+id),manager,null,403);
        send(delete("/api/orders/"+order),manager,null,403);
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code IN('quotation:delete','order:delete')");
        send(delete("/api/quotations/"+id),sales,null,403);
        send(delete("/api/orders/"+order),sales,null,403);
        assertFalse(send(get("/api/quotations/"+id),sales,null,200).path("actions").toString().contains("delete"));
        assertFalse(detail(sales).path("actions").toString().contains("delete"));
    }

    @Test void quotationWithHistoricalOrderReferencesCannotBeDeleted() throws Exception {
        send(delete("/api/orders/"+order),sales,null,200);
        jdbc.update("UPDATE crm_quotation SET is_final=0,status='DRAFT' WHERE id=?",quote);
        send(delete("/api/quotations/"+quote),sales,null,400);
        assertEquals(0,jdbc.queryForObject("SELECT deleted FROM crm_quotation WHERE id=?",Integer.class,quote));
    }

    @Test void cancelledDraftCanBeDeletedWithoutLosingCancellationReasonOrLines() throws Exception {
        send(post("/api/orders/"+order+"/cancel"),sales,Map.of("reason","客户暂缓采购"),200);
        assertTrue(detail(sales).path("actions").toString().contains("delete"));
        send(delete("/api/orders/"+order),other,null,403);
        send(delete("/api/orders/"+order),sales,null,200);
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM crm_sales_order WHERE id=?",String.class,order));
        assertEquals("客户暂缓采购",jdbc.queryForObject("SELECT cancel_reason FROM crm_sales_order WHERE id=?",String.class,order));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM crm_sales_order_item WHERE order_id=?",Integer.class,order));
        send(get("/api/orders/"+order),sales,null,400);
        send(delete("/api/orders/"+order),sales,null,400);
    }

    @Test void cancelledFormalOrderCanBeDeletedWhileKeepingDeliveryHistory() throws Exception {
        confirm();
        send(post("/api/orders/"+order+"/cancel-confirmed"),manager,Map.of("version",detail(manager).path("version").asInt(),"reason","客户取消采购"),200);
        send(delete("/api/orders/"+order),manager,null,200);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM crm_order_delivery WHERE order_id=?",Integer.class,order));
        assertEquals(1,jdbc.queryForObject("SELECT deleted FROM crm_sales_order WHERE id=?",Integer.class,order));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM crm_sales_order_event WHERE order_id=?",Integer.class,order)>=4);
        send(get("/api/deliveries/"+order),manager,null,400);
    }

    @Test void cancelledStatusCannotHideAnEffectiveShipment() throws Exception {
        confirm();
        jdbc.update("INSERT INTO crm_shipment(order_id,request_key,shipment_no,shipped_at,recipient_name,recipient_phone,shipping_address,carrier,tracking_no,actor_id) VALUES(?,?,?,NOW(),'测试收货人','000','测试地址','测试物流','TEST',?)",order,code(),code(),sales.id);
        // 模拟外部导入产生的状态矛盾：有效发货记录必须优先受保护。
        jdbc.update("UPDATE crm_sales_order SET status='CANCELLED' WHERE id=?",order);
        send(delete("/api/orders/"+order),manager,null,400);
        assertEquals(0,jdbc.queryForObject("SELECT deleted FROM crm_sales_order WHERE id=?",Integer.class,order));
        jdbc.update("UPDATE crm_shipment SET status='VOID',void_reason='录入错误' WHERE order_id=?",order);
        send(delete("/api/orders/"+order),manager,null,200);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM crm_shipment WHERE order_id=?",Integer.class,order));
    }

    @Test void voidQuotationCanBeDeletedWithItsReasonAndItemsPreserved() throws Exception {
        long id=draftQuote();
        send(post("/api/quotations/"+id+"/transition"),sales,Map.of("action","VOID","note","客户不采用此方案"),200);
        assertTrue(send(get("/api/quotations/"+id),sales,null,200).path("actions").toString().contains("delete"));
        send(delete("/api/quotations/"+id),other,null,403);
        send(delete("/api/quotations/"+id),sales,null,200);
        assertEquals("VOID",jdbc.queryForObject("SELECT status FROM crm_quotation WHERE id=?",String.class,id));
        assertEquals("客户不采用此方案",jdbc.queryForObject("SELECT note FROM crm_quotation_event WHERE quotation_id=? AND action='VOID'",String.class,id));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM crm_quotation_item WHERE quotation_id=?",Integer.class,id));
        send(get("/api/quotations/"+id),sales,null,400);
        send(delete("/api/quotations/"+id),sales,null,400);
    }

    @Test void deletingVoidQuotationKeepsCancelledOrderReadableAndItsSnapshotUnchanged() throws Exception {
        send(post("/api/orders/"+order+"/cancel"),sales,Map.of("reason","客户取消采购"),200);
        send(post("/api/quotations/"+quote+"/transition"),sales,Map.of("action","VOID","note","原采购方案结束"),200);
        send(delete("/api/quotations/"+quote),sales,null,200);
        var history=detail(sales);
        assertEquals("CANCELLED",history.path("status").asText());
        assertEquals(quote,history.path("quotationId").asLong());
        assertEquals("ACCEPTED",history.path("sourceSnapshot").path("quotation").path("status").asText());
        assertEquals(1,history.path("sourceSnapshot").path("items").size());
        assertEquals(1,send(get("/api/orders").param("keyword","订单维护客户"+sales.id),sales,null,200).path("total").asInt());
        send(delete("/api/orders/"+order),sales,null,200);
        assertEquals(1,jdbc.queryForObject("SELECT deleted FROM crm_quotation WHERE id=?",Integer.class,quote));
    }

    @Test void deletingVoidParentKeepsLaterVersionUsableAndDoesNotReuseItsVersionNumber() throws Exception {
        long parent=draftQuote();jdbc.update("UPDATE crm_quotation SET status='SENT' WHERE id=?",parent);
        long child=send(post("/api/quotations/"+parent+"/revision"),sales,Map.of("reason","修订新方案"),200).asLong();
        send(post("/api/quotations/"+parent+"/transition"),sales,Map.of("action","VOID","note","旧版不再使用"),200);
        send(delete("/api/quotations/"+parent),sales,null,200);
        var data=send(get("/api/quotations/"+child),sales,null,200);
        assertEquals(2,data.path("versionNo").asInt());assertEquals(1,data.path("versions").size());
        jdbc.update("UPDATE crm_quotation SET status='SENT' WHERE id=?",child);
        long next=send(post("/api/quotations/"+child+"/revision"),sales,Map.of("reason","继续修订"),200).asLong();
        assertEquals(3,send(get("/api/quotations/"+next),sales,null,200).path("versionNo").asInt());
    }

    @Test void voidStatusCannotHideQuotationWithAnEffectiveOrder() throws Exception {
        // 模拟外部导入的状态矛盾，不能通过作废标记隐藏有效订单来源。
        jdbc.update("UPDATE crm_quotation SET is_final=0,status='VOID' WHERE id=?",quote);
        send(delete("/api/quotations/"+quote),sales,null,400);
        assertEquals(0,jdbc.queryForObject("SELECT deleted FROM crm_quotation WHERE id=?",Integer.class,quote));
    }

    private long draftQuote() {
        Quotation draft=new Quotation();org.springframework.beans.BeanUtils.copyProperties(quotes.find(quote),draft);
        draft.setId(null);draft.setQuoteNo(code());draft.setStatus("DRAFT");draft.setIsFinal(false);draft.setVersionNo(1);draft.setPreviousId(null);
        quotes.insert(draft);
        for(var item:quotes.items(quote)) {item.setId(null);item.setQuotationId(draft.getId());quotes.insertItem(item);}
        return draft.getId();
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
