package com.crm.mapper;

import com.crm.entity.*;
import com.crm.vo.*;
import com.crm.security.DataScope;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface WorkflowMapper {
    List<WorkflowGrantVO> grants(Long userId);
    List<WorkflowOwnerVO> owners();
    LeadVO lead(@Param("id") Long id,@Param("lock") boolean lock);
    List<LeadVO> leads(@Param("scope") DataScope scope,@Param("keyword") String keyword,@Param("status") String status,@Param("offset") int offset,@Param("size") int size);
    long leadCount(@Param("scope") DataScope scope,@Param("keyword") String keyword,@Param("status") String status);
    void insertLead(Lead lead);
    void updateLead(Lead lead);
    void leadState(Lead lead);
    CustomerVO customer(@Param("id") Long id,@Param("lock") boolean lock);
    CustomerVO customerByName(String name);
    List<CustomerVO> customers(@Param("scope") DataScope scope,@Param("pool") boolean pool,@Param("keyword") String keyword,@Param("offset") int offset,@Param("size") int size);
    long customerCount(@Param("scope") DataScope scope,@Param("pool") boolean pool,@Param("keyword") String keyword);
    void insertCustomer(Customer customer);
    void updateCustomer(Customer customer);
    void customerOwner(Customer customer);
    void insertFollow(FollowRecord record);
    void followDates(FollowRecord record);
    List<FollowVO> resourceFollows(@Param("leadId") Long leadId,@Param("customerId") Long customerId,@Param("sourceLeadId") Long sourceLeadId);
    List<FollowVO> followPage(@Param("scope") DataScope scope,@Param("keyword") String keyword,@Param("due") String due,@Param("offset") int offset,@Param("size") int size);
    long followCount(@Param("scope") DataScope scope,@Param("keyword") String keyword,@Param("due") String due);
    void insertPoolRequest(PoolRequest request);
    PoolRequest poolRequest(Long id);
    List<PoolRequest> customerPoolRequests(Long customerId);
    void reviewPoolRequest(PoolRequest request);
    void insertEvent(OwnershipEvent event);
    List<OwnershipEvent> events(@Param("type") String type,@Param("id") Long id);
    OpportunityVO opportunity(Long id);
    OpportunityVO lockOpportunity(Long id);
    List<Long> activeOpportunityIds(Long customerId);
    List<OpportunityVO> opportunities(@Param("scope") DataScope scope,@Param("keyword") String keyword,@Param("customerId") Long customerId,@Param("stage") String stage,@Param("offset") int offset,@Param("size") int size);
    long opportunityCount(@Param("scope") DataScope scope,@Param("keyword") String keyword,@Param("customerId") Long customerId,@Param("stage") String stage);
    void insertOpportunity(Opportunity opportunity);
    void updateOpportunity(Opportunity opportunity);
}
