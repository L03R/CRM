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
class WorkflowApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    record Actor(long id,String token) {}
    Actor market,manager,east,south,colleague;
    long eastDept,southDept;

    @BeforeEach void prepare() throws Exception {
        eastDept=dept("DEPT_SALES_EAST");southDept=dept("DEPT_SALES_SOUTH");
        market=actor("MARKETING",dept("DEPT_MARKETING"));manager=actor("SALES_MANAGER",dept("DEPT_SALES"));
        east=actor("SALES_STAFF",eastDept);south=actor("SALES_STAFF",southDept);colleague=actor("SALES_STAFF",eastDept);
    }
    @Test void fullLeadCustomerOpportunityAndPublicPoolLifecycle() throws Exception {
        long lead=lead(market,code());assertEquals("PENDING",read("/api/leads/"+lead,market).path("status").asText());
        send(post("/api/leads/"+lead+"/assign"),manager,Map.of("ownerId",east.id,"reason","按华东区域分配"),200);
        follow("leads",lead,east);
        long customer=send(post("/api/leads/"+lead+"/convert"),east,null,200).asLong();
        assertEquals(customer,send(post("/api/leads/"+lead+"/convert"),east,null,200).asLong());
        assertEquals(lead,read("/api/customers/"+customer,east).path("sourceLeadId").asLong());
        assertEquals("CONVERTED",read("/api/leads/"+lead,market).path("status").asText());
        assertEquals(1,read("/api/customers/"+customer+"/follows",east).size());
        assertEquals("LEAD",read("/api/customers/"+customer+"/follows",east).get(0).path("resourceType").asText());
        Map<String,Object> opportunity=opportunity(customer);long business=send(post("/api/opportunities"),east,opportunity,200).asLong();
        send(post("/api/customers/"+customer+"/pool-requests"),east,Map.of("reason","暂时无法继续跟进"),400);
        opportunity.put("stage","LOST");opportunity.put("closeReason","本次采购暂缓");
        send(put("/api/opportunities/"+business),east,opportunity,200);
        long request=send(post("/api/customers/"+customer+"/pool-requests"),east,Map.of("reason","重新安排销售开发"),200).asLong();
        send(post("/api/customers/"+customer+"/pool-requests/"+request+"/review"),manager,Map.of("approve",true,"note","确认无未结束商机，同意回收"),200);
        assertTrue(read("/api/customers/"+customer,east).path("ownerId").isNull());
        send(post("/api/customers/"+customer+"/claim"),colleague,null,200);
        assertEquals(colleague.id,read("/api/customers/"+customer,colleague).path("ownerId").asLong());
        expectGet("/api/customers/"+customer,east,403);
        assertEquals(1,read("/api/customers/"+customer+"/follows",colleague).size());
        assertTrue(read("/api/customers/"+customer+"/events",colleague).toString().contains("CLAIM"));
    }
    @Test void differentSalesCannotReadOrModifyEachOthersData() throws Exception {
        long lead=lead(east,code()),customer=customer(east,code());
        for(String url:List.of("/api/leads/"+lead,"/api/leads/"+lead+"/follows","/api/leads/"+lead+"/events","/api/customers/"+customer,"/api/customers/"+customer+"/follows","/api/customers/"+customer+"/events","/api/customers/"+customer+"/pool-requests"))expectGet(url,colleague,403);
        send(post("/api/leads/"+lead+"/follows"),colleague,followBody(),403);
        send(post("/api/customers/"+customer+"/follows"),colleague,followBody(),403);
        send(put("/api/customers/"+customer),colleague,customerBody(code()),403);
        send(post("/api/opportunities"),colleague,opportunity(customer),403);
        assertEquals(0,read("/api/leads?keyword="+company(lead),colleague).path("total").asInt());
        assertEquals(0,read("/api/customers?keyword="+customerName(customer),colleague).path("total").asInt());
    }
    @Test void marketCanTrackSubmittedLeadButCannotEditItAfterAssignment() throws Exception {
        Map<String,Object> body=leadBody(code());body.put("ownerId",south.id);body.put("status","CONVERTED");
        long id=send(post("/api/leads"),market,body,200).asLong();
        assertTrue(read("/api/leads/"+id,market).path("ownerId").isNull());
        send(post("/api/leads/"+id+"/assign"),manager,Map.of("ownerId",east.id,"reason","分配销售"),200);
        assertEquals(east.id,read("/api/leads/"+id,market).path("ownerId").asLong());
        send(put("/api/leads/"+id),market,body,403);
        send(post("/api/leads/"+id+"/follows"),market,followBody(),403);
        send(post("/api/leads/"+id+"/convert"),market,null,403);
    }
    @Test void conversionRequiresCommunicationAndRejectsClosedOrUnassignedLeads() throws Exception {
        long id=lead(east,code());send(post("/api/leads/"+id+"/convert"),east,null,400);
        send(post("/api/leads/"+id+"/return"),east,Map.of("reason","还未核实，交回经理安排"),200);
        send(post("/api/leads/"+id+"/convert"),east,null,403);
        assertEquals("PENDING",read("/api/leads/"+id,east).path("status").asText());
        send(post("/api/leads/"+id+"/assign"),manager,Map.of("ownerId",east.id,"reason","重新安排"),200);
        send(post("/api/leads/"+id+"/close"),east,Map.of("status","INVALID","reason","联系方式无效"),200);
        send(post("/api/leads/"+id+"/convert"),east,null,400);
        send(post("/api/leads/"+id+"/follows"),east,followBody(),400);
    }
    @Test void returnToQueueKeepsHistoryAndCanBeReassigned() throws Exception {
        long id=lead(market,code());
        send(post("/api/leads/"+id+"/assign"),manager,Map.of("ownerId",east.id,"reason","初次安排"),200);
        follow("leads",id,east);
        send(post("/api/leads/"+id+"/return"),east,Map.of("reason","需要其他销售继续核实"),200);
        send(post("/api/leads/"+id+"/assign"),manager,Map.of("ownerId",colleague.id,"reason","交接给同区域同事"),200);
        assertEquals(1,read("/api/leads/"+id+"/follows",colleague).size());
        assertTrue(read("/api/leads/"+id+"/events",manager).toString().contains("RETURN"));
    }
    @Test void duplicateConversionDoesNotCreateDuplicateCustomer() throws Exception {
        String name=code();long first=lead(east,name);follow("leads",first,east);
        long customer=send(post("/api/leads/"+first+"/convert"),east,null,200).asLong();
        Map<String,Object> second=leadBody(name);second.put("phone","00099999999");
        long id=send(post("/api/leads"),east,second,200).asLong();follow("leads",id,east);
        send(post("/api/leads/"+id+"/convert"),east,null,400);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM crm_customer WHERE company_name=?",Integer.class,name));
        assertEquals(first,read("/api/customers/"+customer,east).path("sourceLeadId").asLong());
    }
    @Test void customerWithoutOpportunityStaysOwnedUntilApprovedReturn() throws Exception {
        long id=customer(east,code());
        assertEquals(east.id,read("/api/customers/"+id,east).path("ownerId").asLong());
        long request=send(post("/api/customers/"+id+"/pool-requests"),east,Map.of("reason","本人申请转其他同事开发"),200).asLong();
        send(post("/api/customers/"+id+"/pool-requests"),east,Map.of("reason","重复申请"),400);
        assertEquals(east.id,read("/api/customers/"+id,east).path("ownerId").asLong());
        send(post("/api/customers/"+id+"/pool-requests/"+request+"/review"),east,Map.of("approve",true,"note","自己审核"),403);
        send(post("/api/customers/"+id+"/pool-requests/"+request+"/review"),manager,Map.of("approve",false,"note","请继续定期维护"),200);
        assertEquals(east.id,read("/api/customers/"+id,east).path("ownerId").asLong());
    }
    @Test void newOpenOpportunityAfterApplicationBlocksApprovalButCanBeRejected() throws Exception {
        long id=customer(east,code());long request=send(post("/api/customers/"+id+"/pool-requests"),east,Map.of("reason","暂时放弃"),200).asLong();
        send(post("/api/opportunities"),east,opportunity(id),200);
        send(post("/api/customers/"+id+"/pool-requests/"+request+"/review"),manager,Map.of("approve",true,"note","尝试通过"),400);
        assertEquals(east.id,read("/api/customers/"+id,east).path("ownerId").asLong());
        send(post("/api/customers/"+id+"/pool-requests/"+request+"/review"),manager,Map.of("approve",false,"note","有新商机，继续跟进"),200);
    }
    @Test void protectedCustomerCannotEnterPoolAndSalesCannotRemoveProtection() throws Exception {
        long id=customer(east,code());Map<String,Object> edit=customerBody(customerName(id));edit.put("protectedCustomer",true);
        send(put("/api/customers/"+id),manager,edit,200);
        edit.put("protectedCustomer",false);send(put("/api/customers/"+id),east,edit,403);
        send(post("/api/customers/"+id+"/pool-requests"),east,Map.of("reason","不再跟进"),400);
        assertTrue(read("/api/customers/"+id,east).path("protectedCustomer").asBoolean());
    }
    @Test void poolIsRegionalAndCanOnlyBeClaimedOnce() throws Exception {
        long id=customer(east,code());release(id);
        expectGet("/api/customers/"+id,south,403);
        send(post("/api/customers/"+id+"/claim"),south,null,403);
        send(post("/api/customers/"+id+"/claim"),colleague,null,200);
        send(post("/api/customers/"+id+"/claim"),east,null,400);
        assertEquals(colleague.id,read("/api/customers/"+id,colleague).path("ownerId").asLong());
    }
    @Test void customerTransferChangesWhoCanReadOldFollowRecordsAndOpportunities() throws Exception {
        long id=customer(east,code());follow("customers",id,east);send(post("/api/opportunities"),east,opportunity(id),200);
        send(post("/api/customers/"+id+"/assign"),manager,Map.of("ownerId",south.id,"reason","跨区域业务交接"),200);
        expectGet("/api/customers/"+id,east,403);
        assertEquals(1,read("/api/customers/"+id+"/follows",south).size());
        assertEquals(1,read("/api/opportunities?customerId="+id,south).path("total").asInt());
        assertEquals(0,read("/api/opportunities?customerId="+id,east).path("total").asInt());
    }
    @Test void unrelatedAllScopeRoleDoesNotWidenCustomerOrOpportunityAccess() throws Exception {
        String role=code();jdbc.update("INSERT INTO sys_role(role_code,role_name,data_scope) VALUES(?,?,1)",role,"无关全部范围");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=?",east.id,role);
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code='product:list' WHERE r.role_code=?",role);
        long id=customer(south,code());expectGet("/api/customers/"+id,east,403);
        send(post("/api/opportunities"),east,opportunity(id),403);
    }
    @Test void regionalManagerCannotAssignOutsideGrantedDepartmentRange() throws Exception {
        Actor regional=actor("SALES_MANAGER",eastDept);
        Map<String,Object> body=leadBody(code());body.put("targetDeptId",southDept);
        long lead=send(post("/api/leads"),market,body,200).asLong();
        expectGet("/api/leads/"+lead,regional,403);
        send(post("/api/leads/"+lead+"/assign"),regional,Map.of("ownerId",south.id,"reason","越区域分配"),403);
        JsonNode owners=read("/api/workflow/owners?kind=LEAD",regional);
        for(JsonNode owner:owners)assertEquals(eastDept,owner.path("deptId").asLong());
    }
    @Test void disabledSalesCannotReceiveAssignedLead() throws Exception {
        long id=lead(market,code());jdbc.update("UPDATE sys_user SET status=0 WHERE id=?",east.id);
        send(post("/api/leads/"+id+"/assign"),manager,Map.of("ownerId",east.id,"reason","分配给已禁用员工"),400);
        assertTrue(read("/api/leads/"+id,market).path("ownerId").isNull());
    }
    @Test void reviewCannotUseAnotherCustomersRequest() throws Exception {
        long one=customer(east,code()),two=customer(east,code());
        long request=send(post("/api/customers/"+one+"/pool-requests"),east,Map.of("reason","待回收"),200).asLong();
        send(post("/api/customers/"+two+"/pool-requests/"+request+"/review"),manager,Map.of("approve",true,"note","不匹配申请"),400);
        assertEquals(east.id,read("/api/customers/"+two,east).path("ownerId").asLong());
    }
    @Test void opportunityCannotChangeCustomerOrReopenClosedStage() throws Exception {
        long id=customer(east,code()),other=customer(east,code());
        Map<String,Object> body=opportunity(id);long business=send(post("/api/opportunities"),east,body,200).asLong();
        body.put("customerId",other);send(put("/api/opportunities/"+business),east,body,400);
        body.put("customerId",id);body.put("stage","LOST");body.put("closeReason","预算取消");send(put("/api/opportunities/"+business),east,body,200);
        body.put("stage","INITIAL");send(put("/api/opportunities/"+business),east,body,400);
    }
    @Test void malformedInputsAreRejectedWithoutBusinessWrites() throws Exception {
        Map<String,Object> body=leadBody(code());body.put("phone","");body.put("email","");
        send(post("/api/leads"),market,body,400);
        long id=lead(east,code());Map<String,Object> follow=followBody();follow.put("occurredAt",LocalDateTime.now().plusDays(1).toString());
        send(post("/api/leads/"+id+"/follows"),east,follow,400);
        assertTrue(read("/api/leads/"+id+"/follows",east).isEmpty());
        send(post("/api/leads/"+id+"/close"),east,Map.of("status","INVALID","reason"," "),400);
    }
    @Test void pagesAndSearchOnlyCountAuthorizedRecords() throws Exception {
        String prefix=code();customer(east,prefix+"甲");customer(east,prefix+"乙");customer(colleague,prefix+"丙");
        JsonNode page=read("/api/customers?keyword="+prefix+"&size=1",east);
        assertEquals(2,page.path("total").asInt());assertEquals(1,page.path("records").size());
        assertEquals(2,read("/api/customers?keyword="+prefix+"&ownerId="+colleague.id+"&all=true&size=1",east).path("total").asInt());
        expectGet("/api/leads?size=101",east,400);
    }
    @Test void nonSalesRolesCannotUseWorkflowEndpoints() throws Exception {
        for(String role:List.of("PRODUCTION","FINANCE","SUPPLY_STAFF")){
            Actor actor=actor(role,dept("DEPT_HQ"));
            for(String path:List.of("/api/leads","/api/customers","/api/customers/pool","/api/customers/follows","/api/opportunities"))expectGet(path,actor,403);
        }
    }
    @Test void salespersonCreatedLeadIsOwnedAndMarketingOnlyCreatesUnassigned() throws Exception {
        long own=lead(east,code()),pending=lead(market,code());
        assertEquals(east.id,read("/api/leads/"+own,east).path("ownerId").asLong());
        assertEquals("CONTACTING",read("/api/leads/"+own,east).path("status").asText());
        assertTrue(read("/api/leads/"+pending,market).path("ownerId").isNull());
        assertFalse(read("/api/leads/"+pending,market).path("actions").toString().contains("assign"));
    }
    @Test void managerCanAssignPublicPoolCustomerWithoutClaimingItFirst() throws Exception {
        long id=customer(east,code());release(id);
        send(post("/api/customers/"+id+"/assign"),manager,Map.of("ownerId",south.id,"reason","经理调整区域后指定销售负责"),200);
        assertEquals(south.id,read("/api/customers/"+id,south).path("ownerId").asLong());
        assertEquals(southDept,read("/api/customers/"+id,south).path("poolDeptId").asLong());
        expectGet("/api/customers/"+id,east,403);
    }

    @Test void opportunityStageFilterCombinesWithKeywordCustomerAndPagination() throws Exception {
        String prefix=code();long customer=customer(east,prefix);
        for(String stage:List.of("INITIAL","QUALIFIED","PROPOSAL","NEGOTIATION","WON","LOST")) {
            var body=opportunity(customer);body.put("title",prefix+stage);body.put("stage",stage);
            if(Set.of("WON","LOST").contains(stage)) body.put("closeReason","测试结果说明");
            send(post("/api/opportunities"),east,body,200);
        }
        assertEquals(6,read("/api/opportunities?keyword="+prefix,east).path("total").asInt());
        for(String stage:List.of("INITIAL","QUALIFIED","PROPOSAL","NEGOTIATION","WON","LOST")) {
            var result=send(get("/api/opportunities").param("keyword",prefix).param("customerId",Long.toString(customer)).param("stage",stage).param("size","1"),east,null,200);
            assertEquals(1,result.path("total").asInt());assertEquals(1,result.path("records").size());
            assertEquals(stage,result.path("records").get(0).path("stage").asText());
            assertEquals(0,send(get("/api/opportunities").param("keyword",prefix).param("stage",stage).param("size","1").param("page","2"),east,null,200).path("records").size());
        }
        assertEquals(6,read("/api/opportunities?keyword="+prefix+"&stage=",east).path("total").asInt());
        assertEquals(0,read("/api/opportunities?keyword="+code()+"&stage=PROPOSAL",east).path("total").asInt());
        expectGet("/api/opportunities?stage=UNKNOWN",east,400);
    }

    @Test void stageFilterDoesNotExposeOtherSalesOpportunities() throws Exception {
        String prefix=code();long own=customer(east,prefix+"A"),another=customer(colleague,prefix+"B");
        for(long customerId:List.of(own,another)) {
            var body=opportunity(customerId);body.put("stage","PROPOSAL");body.put("title",prefix);
            send(post("/api/opportunities"),customerId==own?east:colleague,body,200);
        }
        assertEquals(1,read("/api/opportunities?keyword="+prefix+"&stage=PROPOSAL",east).path("total").asInt());
        assertEquals(2,read("/api/opportunities?keyword="+prefix+"&stage=PROPOSAL",manager).path("total").asInt());
        assertEquals(0,read("/api/opportunities?keyword="+prefix+"&stage=PROPOSAL&customerId="+another,east).path("total").asInt());
        assertEquals(0,read("/api/opportunities?keyword="+prefix+"&stage=PROPOSAL",south).path("total").asInt());
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
    private long customer(Actor actor,String name) throws Exception{return send(post("/api/customers"),actor,customerBody(name),200).asLong();}
    private Map<String,Object> leadBody(String name){Map<String,Object> body=base(name);body.put("demand","拟采购吉他，待沟通确认");body.put("targetDeptId",eastDept);return body;}
    private Map<String,Object> customerBody(String name){Map<String,Object> body=base(name);body.put("relationship","POTENTIAL");body.put("protectedCustomer",false);return body;}
    private Map<String,Object> base(String name){return new HashMap<>(Map.of("companyName",name,"customerType","STORE","region","测试地区","source","测试展会","contactName","测试联系人","phone","00000000001","email","workflow@example.com"));}
    private Map<String,Object> followBody(){return new HashMap<>(Map.of("method","PHONE","content","确认客户身份和采购方向，下次继续沟通","occurredAt",LocalDateTime.now().minusMinutes(1).toString(),"nextFollowAt",LocalDateTime.now().plusDays(1).toString()));}
    private void follow(String resource,long id,Actor actor) throws Exception{send(post("/api/"+resource+"/"+id+"/follows"),actor,followBody(),200);}
    private Map<String,Object> opportunity(long id){return new HashMap<>(Map.of("customerId",id,"title","测试吉他采购意向","expectedAmount",12000,"stage","INITIAL","remark","仅集成测试"));}
    private void release(long id) throws Exception{long req=send(post("/api/customers/"+id+"/pool-requests"),east,Map.of("reason","经理重新分配"),200).asLong();send(post("/api/customers/"+id+"/pool-requests/"+req+"/review"),manager,Map.of("approve",true,"note","审核通过"),200);}
    private String company(long id){return jdbc.queryForObject("SELECT company_name FROM crm_lead WHERE id=?",String.class,id);}
    private String customerName(long id){return jdbc.queryForObject("SELECT company_name FROM crm_customer WHERE id=?",String.class,id);}
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
