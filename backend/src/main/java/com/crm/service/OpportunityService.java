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
public class OpportunityService {
    private final WorkflowMapper mapper;
    private final WorkflowAccess access;
    private final WorkflowService flow;
    public PageResult<OpportunityVO> page(User user,String keyword,Long customerId,String stage,PageQuery page) {
        if(stage!=null && stage.isBlank()) stage=null;
        if(stage!=null && !Set.of("INITIAL","QUALIFIED","PROPOSAL","NEGOTIATION","WON","LOST").contains(stage)) throw new BusinessException("商机阶段不正确");
        var scopes=access.scopes(user);var scope=access.require(scopes,"business:list");
        var records=mapper.opportunities(scope,keyword,customerId,stage,page.getOffset(),page.getSize());
        records.forEach(b->{var edit=scopes.get("business:edit");if(edit!=null && edit.owns(b.getOwnerId(),b.getOwnerDeptId()))b.getActions().add("edit");});
        records.forEach(b->{
            var read=scopes.get("customer:list");
            if(read==null || !read.owns(b.getOwnerId(),b.getOwnerDeptId())) return;
            var quoteRead=scopes.get("quotation:list");
            var quoteAdd=scopes.get("quotation:add");
            if(quoteRead!=null && quoteRead.owns(b.getOwnerId(),b.getOwnerDeptId()))b.getActions().add("quotationView");
            if(quoteAdd!=null && quoteAdd.owns(b.getOwnerId(),b.getOwnerDeptId()) && !Set.of("WON","LOST").contains(b.getStage()))b.getActions().add("quotationAdd");
        });
        return new PageResult<>(records,mapper.opportunityCount(scope,keyword,customerId,stage));
    }
    public PageResult<CustomerVO> customerOptions(User user,String keyword) {
        var scope=access.require(access.scopes(user),"business:add");
        return new PageResult<>(mapper.customers(scope,false,keyword,0,100),mapper.customerCount(scope,false,keyword));
    }
    @Transactional
    public Long create(OpportunityRequest request,User user) {
        flow.customer(request.customerId(),access.scopes(user),"business:add",true);
        Opportunity b=toEntity(request);b.setCreatedBy(user.getId());mapper.insertOpportunity(b);return b.getId();
    }
    @Transactional
    public void update(Long id,OpportunityRequest request,User user) {
        OpportunityVO existing=mapper.opportunity(id);if(existing==null)throw new BusinessException("商机不存在");
        flow.customer(existing.getCustomerId(),access.scopes(user),"business:edit",true);
        existing=mapper.lockOpportunity(id);
        if(!existing.getCustomerId().equals(request.customerId()))throw new BusinessException("商机所属客户不能修改");
        if(Set.of("WON","LOST").contains(existing.getStage()) && !existing.getStage().equals(request.stage()))throw new BusinessException("已结束商机不能重新开启，请新建采购机会");
        Opportunity b=toEntity(request);b.setId(id);mapper.updateOpportunity(b);
    }
    private Opportunity toEntity(OpportunityRequest request) {
        if(Set.of("WON","LOST").contains(request.stage()) && flow.clean(request.closeReason()).isBlank())throw new BusinessException("结束商机时请填写结果说明");
        Opportunity b=new Opportunity();b.setCustomerId(request.customerId());b.setTitle(request.title().trim());b.setExpectedAmount(request.expectedAmount());
        b.setExpectedCloseDate(request.expectedCloseDate());b.setStage(request.stage());b.setRemark(flow.clean(request.remark()));b.setCloseReason(flow.clean(request.closeReason()));return b;
    }
}
