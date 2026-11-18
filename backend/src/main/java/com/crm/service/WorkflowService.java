package com.crm.service;

import com.crm.common.BusinessException;
import com.crm.dto.FollowRequest;
import com.crm.entity.*;
import com.crm.mapper.WorkflowMapper;
import com.crm.mapper.DeptMapper;
import com.crm.security.*;
import com.crm.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WorkflowService {
    private final WorkflowMapper mapper;
    private final WorkflowAccess access;
    private final DeptMapper departments;
    private final com.crm.mapper.ContactMapper contacts;

    public List<WorkflowDepartmentVO> departments() {
        Set<Long> ids=new HashSet<>();owners().forEach(o->ids.add(o.deptId()));
        return departments.findAll().stream().filter(d->ids.contains(d.getId())).map(d->new WorkflowDepartmentVO(d.getId(),d.getDeptName())).toList();
    }
    public List<WorkflowOwnerVO> assignableOwners(User user,String kind) {
        if(!Set.of("LEAD","CUSTOMER").contains(kind))throw new BusinessException("负责人选项类型不正确");
        DataScope scope=access.require(access.scopes(user),kind.equals("LEAD")?"clue:assign":"customer:assign");
        return owners().stream().filter(o->scope.all() || scope.userId().equals(o.id()) || scope.deptIds().contains(o.deptId())).toList();
    }

    public LeadVO lead(Long id,Map<String,DataScope> scopes,String code,boolean creatorRead,boolean lock) {
        LeadVO lead=mapper.lead(id,lock);
        if(lead==null)throw new BusinessException("线索不存在");
        access.check(access.lead(access.require(scopes,code),lead,creatorRead));
        return lead;
    }
    public CustomerVO customer(Long id,Map<String,DataScope> scopes,String code,boolean lock) {
        CustomerVO customer=mapper.customer(id,lock);
        if(customer==null)throw new BusinessException("客户不存在");
        access.check(access.customer(access.require(scopes,code),customer));
        return customer;
    }
    public void active(Lead lead) {
        if(!Set.of("PENDING","CONTACTING").contains(lead.getStatus()))throw new BusinessException("已转客户、关闭或无效的线索不能继续修改");
    }
    public List<WorkflowOwnerVO> owners() {
        return mapper.owners().stream().filter(owner->{
            User user=new User();user.setId(owner.id());user.setDeptId(owner.deptId());
            var grants=access.scopes(user);
            return grants.containsKey("clue:convert") && grants.containsKey("customer:list");
        }).toList();
    }
    public WorkflowOwnerVO owner(Long id,DataScope scope) {
        WorkflowOwnerVO owner=owners().stream().filter(o->o.id().equals(id)).findFirst().orElseThrow(()->new BusinessException("负责人必须是账号和部门启用、在职且拥有销售跟进权限的员工"));
        access.check(scope.all() || scope.userId().equals(id) || scope.deptIds().contains(owner.deptId()));
        return owner;
    }
    public void targetDepartment(Long id) {
        if(owners().stream().noneMatch(o->o.deptId().equals(id)))throw new BusinessException("请选择有有效销售负责人的意向销售部门");
    }
    public String clean(String value) {return value==null?"":value.trim();}
    public void contact(String phone,String email) {
        if(clean(phone).isBlank() && clean(email).isBlank())throw new BusinessException("手机号和邮箱至少填写一项");
    }
    public void follow(Long leadId,Long customerId,FollowRequest request,User user) {
        if(request.nextFollowAt()!=null && request.nextFollowAt().isBefore(request.occurredAt()))throw new BusinessException("下次跟进时间不能早于本次沟通时间");
        FollowRecord record=new FollowRecord();
        record.setLeadId(leadId);record.setCustomerId(customerId);record.setCreatedBy(user.getId());
        if(customerId!=null) {
            Contact contact=request.contactId()==null?contacts.primary(customerId):contacts.lock(request.contactId());
            if(request.contactId()!=null && (contact==null || !contact.getCustomerId().equals(customerId) || contact.getStatus()!=1))
                throw new BusinessException("请选择该客户的有效联系人");
            if(contact!=null) {record.setContactId(contact.getId());record.setContactNameSnapshot(contact.getContactName());}
        } else {
            if(request.contactId()!=null) throw new BusinessException("线索跟进不能选择客户联系人");
            record.setContactNameSnapshot(mapper.lead(leadId,false).getContactName());
        }
        record.setMethod(request.method());record.setContent(request.content().trim());
        record.setOccurredAt(request.occurredAt());record.setNextFollowAt(request.nextFollowAt());
        mapper.insertFollow(record);mapper.followDates(record);
    }
    public void event(String type,Long id,Long from,Long to,User actor,String action,String reason) {
        OwnershipEvent event=new OwnershipEvent();
        event.setResourceType(type);event.setResourceId(id);event.setFromUserId(from);event.setToUserId(to);
        event.setActorId(actor.getId());event.setAction(action);event.setReason(reason.trim());
        mapper.insertEvent(event);
    }
}
