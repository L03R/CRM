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
public class CustomerService {
    private final WorkflowMapper mapper;
    private final WorkflowAccess access;
    private final WorkflowService flow;
    private final ContactService contacts;
    private final com.crm.mapper.QuotationMapper quotations;
    private final com.crm.mapper.SalesOrderMapper orders;

    public PageResult<CustomerVO> page(User user,boolean pool,String keyword,PageQuery page) {
        var scopes=access.scopes(user);var scope=access.require(scopes,pool?"customer:pool:list":"customer:list");
        var records=mapper.customers(scope,pool,keyword,page.getOffset(),page.getSize());records.forEach(c->actions(c,scopes));
        return new PageResult<>(records,mapper.customerCount(scope,pool,keyword));
    }
    public CustomerVO detail(Long id,User user) {
        var scopes=access.scopes(user);var customer=mapper.customer(id,false);
        if(customer==null)throw new BusinessException("客户不存在");
        if(customer.getOwnerId()==null)access.check(access.require(scopes,"customer:pool:list").pool(customer.getPoolDeptId()));
        else access.check(access.customer(access.require(scopes,"customer:list"),customer));
        actions(customer,scopes);return customer;
    }
    public List<FollowVO> follows(Long id,User user) {var customer=detail(id,user);return mapper.resourceFollows(null,id,customer.getSourceLeadId());}
    public List<OwnershipEvent> events(Long id,User user) {detail(id,user);return mapper.events("CUSTOMER",id);}
    public List<PoolRequest> requests(Long id,User user) {
        detail(id,user);
        // 查询固定客户的审核历史，不接受来自前端的负责人或区域范围。
        return mapper.customerPoolRequests(id);
    }
    @Transactional
    public Long create(CustomerRequest request,User user) {
        var scopes=access.scopes(user);access.require(scopes,"customer:add");
        Customer customer=toEntity(request);
        flow.contact(request.phone(),request.email());
        if(request.contactName()==null || request.contactName().isBlank()) throw new BusinessException("请填写主要联系人姓名");
        if(Boolean.TRUE.equals(request.protectedCustomer()))access.require(scopes,"customer:assign");
        duplicate(customer.getCompanyName(),null);
        customer.setOwnerId(user.getId());customer.setPoolDeptId(user.getDeptId());customer.setCreatedBy(user.getId());
        mapper.insertCustomer(customer);contacts.initialize(customer);flow.event("CUSTOMER",customer.getId(),null,user.getId(),user,"CREATE","新增客户档案");
        return customer.getId();
    }
    @Transactional
    public void update(Long id,CustomerRequest request,User user) {
        var scopes=access.scopes(user);var existing=flow.customer(id,scopes,"customer:edit",true);
        if(!Objects.equals(existing.getProtectedCustomer(),request.protectedCustomer()))access.check(access.customer(scopes.get("customer:assign"),existing));
        Customer customer=toEntity(request);customer.setId(id);duplicate(customer.getCompanyName(),id);mapper.updateCustomer(customer);
    }
    @Transactional
    public void follow(Long id,FollowRequest request,User user) {
        flow.customer(id,access.scopes(user),"customer:follow:add",true);flow.follow(null,id,request,user);
    }
    @Transactional
    public void assign(Long id,AssignOwnerRequest request,User user) {
        var scopes=access.scopes(user);var scope=access.require(scopes,"customer:assign");
        var customer=mapper.customer(id,true);if(customer==null)throw new BusinessException("客户不存在");
        access.check(customer.getOwnerId()==null?scope.pool(customer.getPoolDeptId()):access.customer(scope,customer));
        if(customer.getPendingRequestId()!=null)throw new BusinessException("请先审核该客户的退回公海申请");
        var owner=flow.owner(request.ownerId(),access.require(scopes,"customer:assign"));Long previous=customer.getOwnerId();
        customer.setOwnerId(owner.id());customer.setPoolDeptId(owner.deptId());mapper.customerOwner(customer);
        flow.event("CUSTOMER",id,previous,owner.id(),user,"ASSIGN",request.reason());
    }
    @Transactional
    public Long requestPool(Long id,ReasonRequest request,User user) {
        var customer=flow.customer(id,access.scopes(user),"customer:pool:request",true);
        canRelease(customer);
        if(customer.getPendingRequestId()!=null)throw new BusinessException("已有待审核申请，请勿重复提交");
        PoolRequest record=new PoolRequest();record.setCustomerId(id);record.setRequestedBy(user.getId());record.setReason(request.reason().trim());
        mapper.insertPoolRequest(record);flow.event("CUSTOMER",id,customer.getOwnerId(),customer.getOwnerId(),user,"POOL_REQUEST",request.reason());
        return record.getId();
    }
    @Transactional
    public void reviewPool(Long customerId,Long requestId,PoolReviewRequest request,User user) {
        var scopes=access.scopes(user);var customer=flow.customer(customerId,scopes,"customer:pool:approve",true);
        PoolRequest pending=mapper.poolRequest(requestId);
        if(pending==null || !pending.getCustomerId().equals(customerId) || !"PENDING".equals(pending.getStatus()))throw new BusinessException("该客户的待审核申请不存在或已处理");
        if(request.approve()) {
            canRelease(customer);
            access.check(access.require(scopes,"customer:pool:approve").pool(customer.getPoolDeptId()));
            Long previous=customer.getOwnerId();customer.setOwnerId(null);mapper.customerOwner(customer);
            flow.event("CUSTOMER",customerId,previous,null,user,"POOL_APPROVE",request.note());
        } else flow.event("CUSTOMER",customerId,customer.getOwnerId(),customer.getOwnerId(),user,"POOL_REJECT",request.note());
        pending.setStatus(request.approve()?"APPROVED":"REJECTED");pending.setReviewedBy(user.getId());pending.setReviewNote(request.note().trim());mapper.reviewPoolRequest(pending);
    }
    @Transactional
    public void claim(Long id,User user) {
        var scopes=access.scopes(user);var scope=access.require(scopes,"customer:pool:claim");
        access.require(scopes,"customer:list");
        var customer=mapper.customer(id,true);if(customer==null)throw new BusinessException("客户不存在");
        if(customer.getOwnerId()!=null)throw new BusinessException("客户已被领取或分配，请刷新列表");
        access.check(scope.pool(customer.getPoolDeptId()));
        flow.owner(user.getId(),scope);
        customer.setOwnerId(user.getId());customer.setPoolDeptId(user.getDeptId());mapper.customerOwner(customer);
        flow.event("CUSTOMER",id,null,user.getId(),user,"CLAIM","销售领取公海客户");
    }
    public PageResult<FollowVO> followPage(User user,String keyword,String due,PageQuery page) {
        var scope=access.require(access.scopes(user),"customer:follow:list");
        if(due!=null && !Set.of("TODAY","OVERDUE","ALL").contains(due))throw new BusinessException("跟进筛选条件不正确");
        return new PageResult<>(mapper.followPage(scope,keyword,due,page.getOffset(),page.getSize()),mapper.followCount(scope,keyword,due));
    }
    private void canRelease(CustomerVO customer) {
        if(!orders.unfinished(customer.getId()).isEmpty())throw new BusinessException("客户有未完成销售订单，不能进入公海，请由经理安排业务交接");
        if(!quotations.activeCustomerQuotes(customer.getId()).isEmpty()) throw new BusinessException("客户有已接受或有效期内已发送的报价，请先完成业务交接或作废报价");
        if(Boolean.TRUE.equals(customer.getProtectedCustomer()))throw new BusinessException("重要客户受保护，请先由经理确认交接方式");
        if(!mapper.activeOpportunityIds(customer.getId()).isEmpty())throw new BusinessException("客户有未结束商机，请先完成业务交接或结束商机");
    }
    private void actions(CustomerVO c,Map<String,DataScope> scopes) {
        if(c.getOwnerId()==null) {
            var claim=scopes.get("customer:pool:claim");
            if(claim!=null && scopes.containsKey("customer:list") && claim.pool(c.getPoolDeptId()))c.getActions().add("claim");
            var assign=scopes.get("customer:assign");
            if(assign!=null && assign.pool(c.getPoolDeptId()))c.getActions().add("assign");
            return;
        }
        for(var entry:Map.of("edit","customer:edit","assign","customer:assign","follow","customer:follow:add","poolRequest","customer:pool:request","poolApprove","customer:pool:approve","opportunity","business:add").entrySet())
            if(access.customer(scopes.get(entry.getValue()),c) && (!entry.getKey().equals("poolApprove") || c.getPendingRequestId()!=null))c.getActions().add(entry.getKey());
        if(access.customer(scopes.get("customer:assign"),c))c.getActions().add("protect");
        if(access.customer(scopes.get("contact:add"),c))c.getActions().add("contactAdd");
        if(access.customer(scopes.get("quotation:add"),c) && access.customer(scopes.get("business:list"),c))c.getActions().add("quotationAdd");
    }
    private void duplicate(String name,Long id) {
        var existing=mapper.customerByName(name);
        if(existing!=null && !existing.getId().equals(id))throw new BusinessException("同名客户已存在，请核对后维护原客户，避免重复建档");
    }
    private Customer toEntity(CustomerRequest request) {
        Customer c=new Customer();c.setCompanyName(request.companyName().trim());c.setCustomerType(request.customerType());
        c.setRegion(request.region().trim());c.setSource(request.source().trim());c.setContactName(flow.clean(request.contactName()));
        c.setPhone(flow.clean(request.phone()));c.setEmail(flow.clean(request.email()).toLowerCase(Locale.ROOT));c.setAddress(flow.clean(request.address()));
        c.setRemark(flow.clean(request.remark()));c.setRelationship(request.relationship());c.setProtectedCustomer(request.protectedCustomer());return c;
    }
}
