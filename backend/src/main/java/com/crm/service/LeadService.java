package com.crm.service;

import com.crm.common.*;
import com.crm.dto.*;
import com.crm.entity.*;
import com.crm.mapper.WorkflowMapper;
import com.crm.security.*;
import com.crm.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LeadService {
    private final WorkflowMapper mapper;
    private final WorkflowAccess access;
    private final WorkflowService flow;
    private final ContactService contacts;

    public PageResult<LeadVO> page(User user,String keyword,String status,PageQuery page) {
        var scopes=access.scopes(user);var scope=access.require(scopes,"clue:list");
        var records=mapper.leads(scope,keyword,status,page.getOffset(),page.getSize());
        records.forEach(l->actions(l,scopes));
        return new PageResult<>(records,mapper.leadCount(scope,keyword,status));
    }
    public LeadVO detail(Long id,User user) {
        var scopes=access.scopes(user);var lead=flow.lead(id,scopes,"clue:list",true,false);
        actions(lead,scopes);return lead;
    }
    public List<FollowVO> follows(Long id,User user) {detail(id,user);return mapper.resourceFollows(id,null,null);}
    public List<OwnershipEvent> events(Long id,User user) {detail(id,user);return mapper.events("LEAD",id);}

    @Transactional
    public Long create(LeadRequest request,User user) {
        var scopes=access.scopes(user);access.require(scopes,"clue:add");
        Lead lead=toEntity(request);lead.setCreatedBy(user.getId());
        // 有销售转化权限的员工默认负责自己新增的线索；市场录入保持待分配。
        if(scopes.containsKey("clue:convert")) {lead.setOwnerId(user.getId());lead.setTargetDeptId(user.getDeptId());lead.setStatus("CONTACTING");}
        else {flow.targetDepartment(request.targetDeptId());lead.setStatus("PENDING");}
        mapper.insertLead(lead);flow.event("LEAD",lead.getId(),null,lead.getOwnerId(),user,"CREATE","新增线索");
        return lead.getId();
    }
    @Transactional
    public void update(Long id,LeadRequest request,User user) {
        var scopes=access.scopes(user);var existing=editable(id,scopes,"clue:edit");flow.active(existing);
        Lead lead=toEntity(request);lead.setId(id);
        if(existing.getOwnerId()!=null)lead.setTargetDeptId(existing.getTargetDeptId());
        else flow.targetDepartment(request.targetDeptId());
        mapper.updateLead(lead);
    }
    @Transactional
    public void assign(Long id,AssignOwnerRequest request,User user) {
        var scopes=access.scopes(user);var lead=flow.lead(id,scopes,"clue:assign",false,true);flow.active(lead);
        var owner=flow.owner(request.ownerId(),access.require(scopes,"clue:assign"));
        Long previous=lead.getOwnerId();lead.setOwnerId(owner.id());lead.setStatus("CONTACTING");
        lead.setTargetDeptId(owner.deptId());
        mapper.leadState(lead);
        // 同步意向部门，负责人变更后仍可正确回到本区域待分配池。
        mapper.updateLead(lead);
        flow.event("LEAD",id,previous,owner.id(),user,"ASSIGN",request.reason());
    }
    @Transactional
    public void follow(Long id,FollowRequest request,User user) {
        var lead=flow.lead(id,access.scopes(user),"clue:follow",false,true);flow.active(lead);
        if(lead.getOwnerId()==null)throw new BusinessException("请先分配负责人");
        flow.follow(id,null,request,user);
    }
    @Transactional
    public void returnToQueue(Long id,ReasonRequest request,User user) {
        var lead=flow.lead(id,access.scopes(user),"clue:return",false,true);flow.active(lead);
        if(lead.getOwnerId()==null)throw new BusinessException("线索已在待分配池");
        Long previous=lead.getOwnerId();lead.setOwnerId(null);lead.setStatus("PENDING");mapper.leadState(lead);
        flow.event("LEAD",id,previous,null,user,"RETURN",request.reason());
    }
    @Transactional
    public void close(Long id,LeadCloseRequest request,User user) {
        var lead=editable(id,access.scopes(user),"clue:close");flow.active(lead);
        lead.setStatus(request.status());lead.setCloseReason(request.reason().trim());mapper.leadState(lead);
        flow.event("LEAD",id,lead.getOwnerId(),lead.getOwnerId(),user,request.status(),request.reason());
    }
    @Transactional
    public Long convert(Long id,User user) {
        var scopes=access.scopes(user);var lead=flow.lead(id,scopes,"clue:convert",false,true);
        if("CONVERTED".equals(lead.getStatus()))return lead.getConvertedCustomerId();
        flow.active(lead);
        if(lead.getOwnerId()==null)throw new BusinessException("请先分配负责人");
        flow.owner(lead.getOwnerId(),access.require(scopes,"clue:convert"));
        if(mapper.resourceFollows(id,null,null).isEmpty())throw new BusinessException("请先记录实际沟通，再确认转为客户");
        if(mapper.customerByName(lead.getCompanyName())!=null)throw new BusinessException("同名客户已存在，请核对资料，不要重复转化");
        Customer customer=new Customer();
        customer.setCompanyName(lead.getCompanyName());customer.setCustomerType(lead.getCustomerType());
        customer.setRegion(lead.getRegion());customer.setSource(lead.getSource());
        customer.setContactName(lead.getContactName());customer.setPhone(lead.getPhone());customer.setEmail(lead.getEmail());
        customer.setRemark(lead.getDemand());customer.setOwnerId(lead.getOwnerId());customer.setPoolDeptId(lead.getOwnerDeptId());
        customer.setCreatedBy(user.getId());customer.setSourceLeadId(id);customer.setRelationship("POTENTIAL");customer.setProtectedCustomer(false);
        customer.setLastFollowAt(lead.getLastFollowAt());customer.setNextFollowAt(lead.getNextFollowAt());
        mapper.insertCustomer(customer);contacts.initialize(customer);lead.setStatus("CONVERTED");mapper.leadState(lead);
        flow.event("LEAD",id,lead.getOwnerId(),lead.getOwnerId(),user,"CONVERT","确认有合作价值，转为客户");
        flow.event("CUSTOMER",customer.getId(),null,customer.getOwnerId(),user,"CONVERT","由线索转入，保留原始来源与沟通记录");
        return customer.getId();
    }
    private LeadVO editable(Long id,Map<String,DataScope> scopes,String code) {
        LeadVO lead=mapper.lead(id,true);if(lead==null)throw new BusinessException("线索不存在");
        DataScope scope=access.require(scopes,code);
        access.check(access.lead(scope,lead,false) || lead.getOwnerId()==null && userCreated(scope,lead));
        return lead;
    }
    private boolean userCreated(DataScope scope,Lead lead) {return scope.userId().equals(lead.getCreatedBy());}
    private void actions(LeadVO lead,Map<String,DataScope> scopes) {
        boolean active=Set.of("PENDING","CONTACTING").contains(lead.getStatus());
        if(!active)return;
        for(var entry:Map.of("edit","clue:edit","assign","clue:assign","follow","clue:follow","convert","clue:convert","return","clue:return","close","clue:close").entrySet()){
            DataScope scope=scopes.get(entry.getValue());
            boolean allowed=access.lead(scope,lead,false) || scope!=null && lead.getOwnerId()==null && Set.of("edit","close").contains(entry.getKey()) && userCreated(scope,lead);
            if(allowed && (lead.getOwnerId()!=null || Set.of("edit","assign","close").contains(entry.getKey())))lead.getActions().add(entry.getKey());
        }
    }
    private Lead toEntity(LeadRequest request) {
        flow.contact(request.phone(),request.email());
        Lead lead=new Lead();lead.setCompanyName(request.companyName().trim());lead.setCustomerType(request.customerType());
        lead.setRegion(request.region().trim());lead.setSource(request.source().trim());lead.setContactName(request.contactName().trim());
        lead.setPhone(flow.clean(request.phone()));lead.setEmail(flow.clean(request.email()).toLowerCase(Locale.ROOT));
        lead.setContactKey(lead.getPhone().isBlank()?lead.getEmail():lead.getPhone());lead.setDemand(request.demand().trim());
        lead.setTargetDeptId(request.targetDeptId());lead.setNextFollowAt(request.nextFollowAt());
        return lead;
    }
}
