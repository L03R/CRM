package com.crm.service;

import com.crm.common.*;
import com.crm.dto.*;
import com.crm.entity.*;
import com.crm.mapper.*;
import com.crm.security.*;
import com.crm.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ContactService {
    private final ContactMapper mapper;
    private final WorkflowMapper workflow;
    private final WorkflowAccess access;

    public PageResult<ContactVO> page(User user,Long customerId,String keyword,Integer status,PageQuery page) {
        if(status!=null && status!=0 && status!=1) throw new BusinessException("联系人状态不正确");
        var scopes=access.scopes(user);
        var scope=access.require(scopes,"contact:list");
        boolean pool=false;
        if(customerId!=null) pool=readCustomer(customerId,scopes).getOwnerId()==null;
        var read=pool?scope:access.require(scopes,"customer:list");
        var rows=mapper.page(scope,read,customerId,pool,keyword,status,page.getOffset(),page.getSize());
        Map<Long,CustomerVO> customers=new HashMap<>();
        for(var contact:rows) {
            var customer=customers.computeIfAbsent(contact.getCustomerId(),id->workflow.customer(id,false));
            for(String action:List.of("edit","status","primary"))
                if(access.customer(scopes.get("contact:"+action),customer)) contact.getActions().add(action);
        }
        return new PageResult<>(rows,mapper.count(scope,read,customerId,pool,keyword,status));
    }

    public List<ContactCustomerVO> customers(User user,String keyword) {
        var scopes=access.scopes(user);
        return mapper.customers(access.require(scopes,"contact:add"),access.require(scopes,"customer:list"),keyword);
    }

    @Transactional
    public Long save(Long id,ContactRequest request,User user) {
        var scopes=access.scopes(user);
        var customer=writable(request.customerId(),scopes,id==null?"contact:add":"contact:edit");
        Contact old=id==null?null:required(id);
        if(old!=null && !old.getCustomerId().equals(customer.getId())) throw new BusinessException("联系人所属客户不能修改");
        if(old!=null && !old.getStatus().equals(request.status())) access.check(access.customer(access.require(scopes,"contact:status"),customer));
        Contact contact=toEntity(request);
        contact.setId(id);contact.setIsPrimary(old!=null && Boolean.TRUE.equals(old.getIsPrimary()) && request.status()==1);
        if(id==null) mapper.insert(contact);else mapper.update(contact);
        ensurePrimary(customer.getId());
        return contact.getId();
    }

    @Transactional
    public void primary(Long id,User user) {
        Contact contact=required(id);
        writable(contact.getCustomerId(),access.scopes(user),"contact:primary");
        contact=mapper.lock(id);
        if(contact.getStatus()!=1) throw new BusinessException("停用联系人不能设为主要联系人");
        mapper.clearPrimary(contact.getCustomerId());mapper.setPrimary(id);
    }

    @Transactional
    public void status(Long id,int status,User user) {
        if(status!=0 && status!=1) throw new BusinessException("联系人状态不正确");
        Contact contact=required(id);
        writable(contact.getCustomerId(),access.scopes(user),"contact:status");
        contact=mapper.lock(id);contact.setStatus(status);
        if(status==0) contact.setIsPrimary(false);
        mapper.update(contact);ensurePrimary(contact.getCustomerId());
    }

    // Called inside customer creation/conversion transactions, not a separate public write API.
    public void initialize(Customer customer) {
        Contact contact=new Contact();contact.setCustomerId(customer.getId());contact.setContactName(customer.getContactName());
        contact.setPhone(clean(customer.getPhone()));contact.setEmail(clean(customer.getEmail()));
        contact.setJobTitle("");contact.setLandline("");contact.setWechat("");contact.setRemark("");
        contact.setStatus(1);contact.setIsPrimary(true);mapper.insert(contact);
    }

    private CustomerVO readCustomer(Long id,Map<String,DataScope> scopes) {
        var customer=workflow.customer(id,false);
        if(customer==null) throw new BusinessException("客户不存在");
        var scope=access.require(scopes,"contact:list");
        if(customer.getOwnerId()==null) {
            access.check(scope.pool(customer.getPoolDeptId()));
            access.check(access.require(scopes,"customer:pool:list").pool(customer.getPoolDeptId()));
        } else {
            access.check(access.customer(scope,customer));
            access.check(access.customer(access.require(scopes,"customer:list"),customer));
        }
        return customer;
    }

    private CustomerVO writable(Long id,Map<String,DataScope> scopes,String code) {
        var customer=workflow.customer(id,true);
        if(customer==null) throw new BusinessException("客户不存在");
        access.check(access.customer(access.require(scopes,code),customer));
        access.check(access.customer(access.require(scopes,"customer:list"),customer));
        return customer;
    }

    private Contact required(Long id) {
        var contact=mapper.find(id);if(contact==null) throw new BusinessException("联系人不存在");return contact;
    }
    private void ensurePrimary(Long customerId) {
        if(mapper.primary(customerId)!=null) return;
        Long first=mapper.firstActive(customerId);if(first!=null) mapper.setPrimary(first);
    }
    private Contact toEntity(ContactRequest request) {
        Contact contact=new Contact();contact.setCustomerId(request.customerId());contact.setContactName(request.contactName().trim());
        contact.setPhone(clean(request.phone()));contact.setLandline(clean(request.landline()));contact.setWechat(clean(request.wechat()));
        contact.setEmail(clean(request.email()).toLowerCase(Locale.ROOT));contact.setJobTitle(clean(request.jobTitle()));contact.setRemark(clean(request.remark()));contact.setStatus(request.status());
        if(List.of(contact.getPhone(),contact.getLandline(),contact.getWechat(),contact.getEmail()).stream().allMatch(String::isBlank)) throw new BusinessException("手机、座机、微信和邮箱至少填写一项");
        return contact;
    }
    private String clean(String value) {return value==null?"":value.trim();}
}
