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
class OrderMaintenanceApiTest {
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

    @Test void draftCanChangeProductsPricesAndTermsWithoutChangingSourceQuote() throws Exception {
        var data=content(sales);
        line(data).put("quantity",3);line(data).put("unitPrice",new BigDecimal("120.50"));
        data.put("totalAmount",1);data.put("customerId",999999);
        data.put("paymentTerms","客户确认的新付款条件");
        send(put("/api/orders/"+order),sales,data,200);
        var detail=detail(sales);
        amount("361.50",detail.path("totalAmount"));
        assertEquals(customer,detail.path("customerId").asLong());
        assertEquals("原始吉他快照",detail.path("items").get(0).path("productName").asText());
        amount("200",detail.path("sourceSnapshot").path("quotation").path("totalAmount"));
        assertEquals(2,quotes.items(quote).get(0).getQuantity());
        assertEquals(1,detail.path("version").asInt());
        assertEquals("EDIT",detail.path("events").get(0).path("action").asText());
        amount("200",detail.path("events").get(0).path("beforeContent").path("order").path("totalAmount"));
        amount("361.50",detail.path("events").get(0).path("afterContent").path("order").path("totalAmount"));
        send(post("/api/orders/"+order+"/confirm"),sales,null,200);
    }

    @Test void confirmedChangeNeedsOwnPermissionReasonAndCustomerConfirmation() throws Exception {
        confirm();
        var data=content(manager);line(data).put("quantity",4);
        var change=new HashMap<String,Object>(Map.of("content",data,"reason","客户增加采购数量","customerConfirmation","客户采购单确认本次修改"));
        send(post("/api/orders/"+order+"/change"),sales,change,403);
        send(put("/api/orders/"+order),manager,data,400);
        change.put("customerConfirmation","");send(post("/api/orders/"+order+"/change"),manager,change,400);
        change.put("customerConfirmation","客户采购单确认本次修改");
        send(post("/api/orders/"+order+"/change"),manager,change,200);
        var detail=detail(manager);
        assertEquals("CONFIRMED",detail.path("status").asText());amount("400",detail.path("totalAmount"));
        var event=detail.path("events").get(0);
        assertEquals("CHANGE",event.path("action").asText());assertEquals("客户增加采购数量",event.path("note").asText());
        assertEquals("客户采购单确认本次修改",detail.path("confirmationNote").asText());
        assertEquals(2,event.path("beforeContent").path("items").get(0).path("quantity").asInt());
        assertEquals(4,event.path("afterContent").path("items").get(0).path("quantity").asInt());
        amount("200",detail.path("sourceSnapshot").path("quotation").path("totalAmount"));
    }

    @Test void staleUpdatesAndInvalidChangesLeaveSavedItemsAndHistoryUnchanged() throws Exception {
        var original=content(sales);
        send(put("/api/orders/"+order),sales,original,200);
        line(original).put("quantity",5);
        send(put("/api/orders/"+order),sales,original,400);
        assertEquals(2,detail(sales).path("events").size());
        assertEquals(2,detail(sales).path("items").get(0).path("quantity").asInt());
        send(post("/api/orders/"+order+"/confirm"),sales,null,200);
        var changeContent=content(manager);line(changeContent).put("quantity",8);changeContent.put("promisedDate",LocalDate.now().minusDays(1).toString());
        send(post("/api/orders/"+order+"/change"),manager,Map.of("content",changeContent,"reason","失败操作","customerConfirmation","失败操作的确认依据"),400);
        var detail=detail(manager);
        amount("200",detail.path("totalAmount"));assertEquals(2,detail.path("items").get(0).path("quantity").asInt());
        assertEquals(3,detail.path("events").size());
        assertEquals(2,detail.path("version").asInt());
    }

    @Test void overdueOrderCanKeepOriginalPromiseWhileChangingContactDetails() throws Exception {
        confirm();
        LocalDate originalDate=LocalDate.now().minusDays(2);
        jdbc.update("UPDATE crm_sales_order SET promised_date=? WHERE id=?",originalDate,order);
        var data=content(manager);
        data.put("promisedDate",originalDate.toString());
        data.put("recipientPhone","13900001234");
        send(post("/api/orders/"+order+"/change"),manager,Map.of("content",data,"reason","客户更新收货电话","customerConfirmation","客户确认收货电话，原交期保留"),200);
        var saved=detail(manager);
        assertEquals("13900001234",saved.path("recipientPhone").asText());
        assertEquals(originalDate.toString(),saved.path("promisedDate").asText());

        data=content(manager);
        data.put("promisedDate",originalDate.plusDays(1).toString());
        send(post("/api/orders/"+order+"/change"),manager,Map.of("content",data,"reason","修改为另一个过去日期","customerConfirmation","客户确认"),400);
        assertEquals(saved.path("version"),detail(manager).path("version"));
    }

    @Test void managerCanCancelConfirmedOrderAndReopenItsQuotationFlow() throws Exception {
        confirm();
        var data=Map.of("version",detail(manager).path("version").asInt(),"reason","客户调整本次采购，需要重新确认报价");
        send(post("/api/orders/"+order+"/cancel-confirmed"),sales,data,403);
        send(post("/api/orders/"+order+"/cancel-confirmed"),manager,data,200);
        assertEquals("CANCELLED",detail(manager).path("status").asText());
        assertEquals("NEGOTIATION",jdbc.queryForObject("SELECT stage FROM crm_opportunity WHERE id=?",String.class,business));
        assertEquals(2,jdbc.queryForObject("SELECT quantity FROM crm_sales_order_item WHERE order_id=?",Integer.class,order));
        long revised=send(post("/api/quotations/"+quote+"/revision"),sales,Map.of("reason","重新报价"),200).asLong();
        assertNotEquals(quote,revised);
        assertEquals(order,send(post("/api/orders/from-quotation/"+quote),sales,null,200).asLong());
    }

    @Test void completedOrdersAndOutOfScopeUsersCannotBeChangedOrCancelled() throws Exception {
        confirm();
        var change=Map.of("content",content(manager),"reason","测试变更","customerConfirmation","测试客户确认");
        send(post("/api/orders/"+order+"/change"),other,change,403);
        send(get("/api/orders/"+order+"/skus"),other,null,403);
        jdbc.update("UPDATE crm_sales_order SET status='COMPLETED' WHERE id=?",order);
        send(post("/api/orders/"+order+"/change"),manager,change,400);
        send(post("/api/orders/"+order+"/cancel-confirmed"),manager,Map.of("version",2,"reason","禁止取消已完成单"),400);
        jdbc.update("UPDATE sys_permission SET status=0 WHERE permission_code='order:change'");
        send(post("/api/orders/"+order+"/change"),manager,change,403);
    }

    @Test void filtersAndNewSkuSelectionUseRealBackendData() throws Exception {
        var skus=send(get("/api/orders/"+order+"/skus"),sales,null,200);
        assertTrue(skus.size()>0);
        JsonNode newSku=null;
        for(var sku:skus) if(sku.path("id").asLong()!=quotes.items(quote).get(0).getSkuId()) {newSku=sku;break;}
        assertNotNull(newSku);
        var data=content(sales);
        data.put("items",List.of(Map.of("skuId",newSku.path("id").asLong(),"quantity",7,"unitPrice",new BigDecimal("10.25"),"requirements","新的包装要求")));
        send(put("/api/orders/"+order),sales,data,200);
        amount("71.75",detail(sales).path("totalAmount"));
        var result=send(get("/api/orders").param("keyword","订单维护客户"+sales.id).param("dateFrom",LocalDate.now().toString()).param("dateTo",LocalDate.now().toString()),sales,null,200);
        assertEquals(1,result.path("total").asInt());
        assertEquals(0,send(get("/api/orders").param("dateFrom",LocalDate.now().plusDays(1).toString()),sales,null,200).path("total").asInt());
        send(get("/api/orders").param("dateFrom","2027-01-01").param("dateTo","2026-01-01"),sales,null,400);
    }

    @Test void overflowAndEmptyProductsAreRejectedWithoutLosingSavedContent() throws Exception {
        var data=content(sales);
        data.put("items",List.of());send(put("/api/orders/"+order),sales,data,400);
        var item=Map.of("skuId",quotes.items(quote).get(0).getSkuId(),"quantity",1000000,"unitPrice",new BigDecimal("99999999.99"));
        data.put("items",List.of(item));send(put("/api/orders/"+order),sales,data,400);
        amount("200",detail(sales).path("totalAmount"));assertEquals(1,detail(sales).path("events").size());
    }

    @Test void changePermissionUsesItsOwnRoleDataScope() throws Exception {
        confirm();
        var change=Map.of("content",content(manager),"reason","测试经理的数据范围","customerConfirmation","客户确认");
        jdbc.update("UPDATE sys_role SET data_scope=4 WHERE role_code='SALES_MANAGER'");
        send(post("/api/orders/"+order+"/change"),manager,change,403);
        send(post("/api/orders/"+order+"/cancel-confirmed"),manager,Map.of("version",2,"reason","测试取消范围"),403);
        assertEquals("CONFIRMED",jdbc.queryForObject("SELECT status FROM crm_sales_order WHERE id=?",String.class,order));
    }

    @Test void confirmationErrorOnlyNamesFieldsThatAreActuallyMissing() throws Exception {
        var saved=content(sales);saved.put("confirmationNote","");
        send(put("/api/orders/"+order),sales,saved,200);
        var response=mvc.perform(post("/api/orders/"+order+"/confirm").header("Authorization","Bearer "+sales.token)).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals("请补齐：正式下单依据",json.readTree(response).path("message").asText());
        assertEquals("DRAFT",detail(sales).path("status").asText());
        saved=content(sales);saved.put("recipientPhone","");saved.put("shippingAddress","");
        send(put("/api/orders/"+order),sales,saved,200);
        response=mvc.perform(post("/api/orders/"+order+"/confirm").header("Authorization","Bearer "+sales.token)).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals("请补齐：收货电话、收货地址",json.readTree(response).path("message").asText());
        send(put("/api/orders/"+order),sales,content(sales),200);
        send(post("/api/orders/"+order+"/confirm"),sales,null,200);
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
