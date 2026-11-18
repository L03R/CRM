package com.crm.service;

import com.crm.common.*;
import com.crm.dto.*;
import com.crm.entity.*;
import com.crm.mapper.*;
import com.crm.security.*;
import com.crm.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service @RequiredArgsConstructor
public class QuotationService {
    private final QuotationMapper mapper;
    private final WorkflowMapper workflow;
    private final ContactMapper contacts;
    private final ProductMapper productMapper;
    private final ProductService products;
    private final WorkflowAccess access;
    private final SalesOrderMapper orders;
    private static final Map<String,String> OPERATIONS=Map.of("SEND","quotation:send","ACCEPT","quotation:accept","REJECT","quotation:accept","FINAL","quotation:final","VOID","quotation:void");
    private static final BigDecimal MAX_AMOUNT=new BigDecimal("999999999999.99");

    public PageResult<QuotationVO> page(User user,Long customerId,Long opportunityId,String keyword,String status,boolean onlyFinal,PageQuery page) {
        if(status!=null && !status.isEmpty() && !Set.of("DRAFT","SENT","ACCEPTED","REJECTED","VOID","EXPIRED").contains(status)) throw new BusinessException("报价状态不正确");
        var scopes=scope(user,"quotation:list");
        var rows=mapper.page(scopes,customerId,opportunityId,keyword,status,onlyFinal,page.getOffset(),page.getSize());
        var grants=access.scopes(user);rows.forEach(q->actions(q,grants));
        return new PageResult<>(rows,mapper.count(scopes,customerId,opportunityId,keyword,status,onlyFinal));
    }

    public QuotationVO detail(Long id,User user) {
        var quote=quote(id,user,"quotation:list",false);
        quote.setItems(mapper.items(id));quote.setEvents(mapper.events(id));quote.setVersions(mapper.versions(quote.getQuoteNo(),scope(user,"quotation:list")));actions(quote,access.scopes(user));return quote;
    }

    public List<ContactCustomerVO> customers(User user,String keyword) {
        var scopes=scope(user,"quotation:add");return contacts.customers(scopes.get("quote"),scopes.get("customer"),keyword);
    }
    public List<OpportunityVO> opportunities(User user,Long customerId,String keyword) {
        return mapper.opportunities(scope(user,"quotation:add"),customerId,keyword);
    }
    public List<ContactVO> contacts(User user,Long customerId,String keyword,String mode) {
        if(!Set.of("CREATE","EDIT").contains(mode)) throw new BusinessException("联系人选择类型不正确");
        var scopes=scope(user,mode.equals("CREATE")?"quotation:add":"quotation:edit");
        customer(customerId,scopes,false);
        return contacts.page(scopes.get("quote"),scopes.get("customer"),customerId,false,keyword,1,0,50);
    }
    public List<QuotationSkuVO> skus(User user,String keyword) {
        var grants=access.scopes(user);access.require(grants,"product:list");
        if(!grants.containsKey("quotation:add") && !grants.containsKey("quotation:edit")) access.require(grants,"quotation:add");
        Map<Long,ProductVO> cache=new HashMap<>();List<QuotationSkuVO> result=new ArrayList<>();
        for(Long id:mapper.skuIds(keyword)) {
            ProductSku sku=productMapper.sku(id);
            ProductVO product=cache.computeIfAbsent(sku.getProductId(),products::detail);
            if(!product.isAvailable()) continue;
            var actual=product.getSkus().stream().filter(s->s.getId().equals(id)).findFirst().orElseThrow();
            result.add(new QuotationSkuVO(id,sku.getSkuCode(),product.getProductName(),product.getModelCode(),product.getUnit(),sku.getSalePrice(),specification(product,actual)));
            if(result.size()==50) break;
        }
        return result;
    }

    @Transactional
    public Long create(QuotationRequest request,User user) {
        var scopes=scope(user,"quotation:add");var customer=customer(request.customerId(),scopes,true);
        opportunity(request.opportunityId(),customer.getId(),true,false);
        Quotation quote=new Quotation();
        quote.setQuoteNo("QT"+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase());
        quote.setVersionNo(1);quote.setCreatedBy(user.getId());quote.setSellerName(user.getRealName());
        quote.setCustomerId(customer.getId());quote.setOpportunityId(request.opportunityId());quote.setStatus("DRAFT");quote.setIsFinal(false);quote.setCurrency("CNY");
        var items=prepare(quote,request,customer,user);mapper.insert(quote);saveItems(quote.getId(),items);
        mapper.event(quote.getId(),user.getId(),"CREATE","创建报价草稿");return quote.getId();
    }

    @Transactional
    public void update(Long id,QuotationRequest request,User user) {
        var quote=quote(id,user,"quotation:edit",true);
        if(!quote.getStatus().equals("DRAFT")) throw new BusinessException("仅草稿可以编辑，已发送报价请修订新版本");
        if(!quote.getCustomerId().equals(request.customerId()) || !quote.getOpportunityId().equals(request.opportunityId())) throw new BusinessException("报价所属客户和商机不能修改");
        opportunity(quote.getOpportunityId(),quote.getCustomerId(),true,false);
        var items=prepare(quote,request,workflow.customer(quote.getCustomerId(),true),user);
        mapper.updateDraft(quote);mapper.deleteItems(id);saveItems(id,items);mapper.event(id,user.getId(),"EDIT","修改报价草稿");
    }

    @Transactional
    public void delete(Long id,User user) {
        var quote=quote(id,user,"quotation:delete",true);
        if(!Set.of("DRAFT","VOID").contains(quote.getStatus())) throw new BusinessException("仅草稿或已作废的报价可以删除");
        if("DRAFT".equals(quote.getStatus()) && mapper.hasReferences(id)) throw new BusinessException("报价已有关联订单或后续版本，不能删除");
        if("VOID".equals(quote.getStatus()) && mapper.hasActiveOrders(id)) throw new BusinessException("报价仍关联未取消的有效订单，不能删除");
        mapper.softDelete(id,user.getId());
        mapper.event(id,user.getId(),"DELETE","逻辑删除"+("VOID".equals(quote.getStatus())?"已作废报价":"报价草稿")+"，原明细与历史保留");
    }

    @Transactional
    public Long revise(Long id,ReasonRequest request,User user) {
        var source=quote(id,user,"quotation:revise",true);
        opportunity(source.getOpportunityId(),source.getCustomerId(),true,false);
        var latest=mapper.latest(source.getQuoteNo());
        if("DRAFT".equals(latest.getStatus()) && Objects.equals(latest.getPreviousId(),id)) return latest.getId();
        if(!latest.getId().equals(id)) throw new BusinessException("请从该报价的最新版本修订");
        if(Set.of("DRAFT","VOID").contains(source.getStatus())) throw new BusinessException("草稿直接编辑，作废报价不能修订");
        var linkedOrder=orders.byQuotation(id);
        if(linkedOrder!=null && !"CANCELLED".equals(linkedOrder.getStatus())) throw new BusinessException("报价已关联订单，请先处理订单，不能直接修订来源报价");
        Quotation next=new Quotation();BeanUtils.copyProperties(source,next);
        next.setId(null);next.setPreviousId(id);next.setVersionNo(mapper.maxVersion(source.getQuoteNo())+1);next.setStatus("DRAFT");next.setIsFinal(false);
        next.setCreatedBy(user.getId());next.setSellerName(user.getRealName());next.setSentAt(null);next.setAcceptedAt(null);mapper.insert(next);
        var items=mapper.items(id);for(var item:items){item.setId(null);item.setQuotationId(next.getId());mapper.insertItem(item);}
        mapper.event(id,user.getId(),"REVISE","创建 V"+next.getVersionNo()+"："+request.reason().trim());
        mapper.event(next.getId(),user.getId(),"CREATE","由 V"+source.getVersionNo()+" 修订："+request.reason().trim());return next.getId();
    }

    @Transactional
    public void transition(Long id,QuotationActionRequest request,User user) {
        var quote=quote(id,user,OPERATIONS.get(request.action()),true);
        opportunity(quote.getOpportunityId(),quote.getCustomerId(),true,request.action().equals("VOID"));
        switch(request.action()) {
            case "SEND" -> {
                requireState(quote,"DRAFT");
                if(quote.getValidUntil().isBefore(LocalDate.now())) throw new BusinessException("报价已超过有效期，请先修改草稿");
                if(quote.getQuoteDate().isAfter(LocalDate.now())) throw new BusinessException("报价日期尚未到达");
                var contact=contacts.lock(quote.getContactId());
                if(contact.getStatus()!=1) throw new BusinessException("联系人已停用，请修改草稿选择有效联系人");
                access.require(access.scopes(user),"product:list");
                for(var item:mapper.items(id)) activeSku(item.getSkuId());
                quote.setStatus("SENT");quote.setSentAt(LocalDateTime.now());
            }
            case "ACCEPT","REJECT" -> {
                requireState(quote,"SENT");
                if(quote.getValidUntil().isBefore(LocalDate.now())) throw new BusinessException("报价已过期，请修订后重新发送");
                quote.setStatus(request.action().equals("ACCEPT")?"ACCEPTED":"REJECTED");
                if(request.action().equals("ACCEPT")) quote.setAcceptedAt(LocalDateTime.now());
            }
            case "FINAL" -> {
                requireState(quote,"ACCEPTED");
                var linkedOrder=orders.activeByOpportunity(quote.getOpportunityId());
                if(linkedOrder!=null && !linkedOrder.getQuotationId().equals(id))throw new BusinessException("商机已有关联订单，不能替换订单来源的最终报价");
                Long previous=mapper.finalId(quote.getOpportunityId());
                if(Objects.equals(previous,id)) return;
                if(previous!=null) mapper.event(previous,user.getId(),"UNFINAL","最终报价改选为 "+quote.getQuoteNo()+" V"+quote.getVersionNo());
                mapper.clearFinal(quote.getOpportunityId());quote.setIsFinal(true);
            }
            case "VOID" -> {
                var linkedOrder=orders.byQuotation(id);
                if(linkedOrder!=null && !"CANCELLED".equals(linkedOrder.getStatus()))throw new BusinessException("报价已关联订单，不能直接作废；请先处理订单");
                if(quote.getStatus().equals("VOID")) throw new BusinessException("报价已作废");
                quote.setStatus("VOID");quote.setIsFinal(false);
            }
        }
        mapper.updateState(quote);mapper.event(id,user.getId(),request.action(),request.note().trim());
    }

    private List<QuotationItem> prepare(Quotation quote,QuotationRequest request,Customer customer,User user) {
        access.require(access.scopes(user),"product:list");
        if(request.validUntil().isBefore(request.quoteDate())) throw new BusinessException("有效期不能早于报价日期");
        Contact contact=contacts.find(request.contactId());
        if(contact==null || !contact.getCustomerId().equals(customer.getId())) throw new BusinessException("联系人必须属于报价客户");
        contact=contacts.lock(contact.getId());if(contact.getStatus()!=1) throw new BusinessException("请选择有效联系人");
        quote.setTitle(request.title().trim());quote.setQuoteDate(request.quoteDate());quote.setValidUntil(request.validUntil());quote.setTaxMode(request.taxMode());
        quote.setPaymentTerms(request.paymentTerms().trim());quote.setDeliveryTerms(request.deliveryTerms().trim());quote.setFreightTerms(clean(request.freightTerms()));quote.setRemark(clean(request.remark()));
        quote.setContactId(contact.getId());quote.setCustomerName(customer.getCompanyName());quote.setContactName(contact.getContactName());quote.setContactPhone(contact.getPhone());
        quote.setContactLandline(contact.getLandline());quote.setContactWechat(contact.getWechat());quote.setContactEmail(contact.getEmail());
        List<QuotationItem> items=new ArrayList<>();BigDecimal total=BigDecimal.ZERO;int sort=0;
        for(var input:request.items()) {
            var product=activeSku(input.skuId());var sku=product.getSkus().stream().filter(s->s.getId().equals(input.skuId())).findFirst().orElseThrow();
            QuotationItem item=new QuotationItem();item.setSkuId(sku.getId());item.setSortNo(++sort);item.setProductName(product.getProductName());item.setModelCode(product.getModelCode());
            item.setSkuCode(sku.getSkuCode());item.setUnit(product.getUnit());item.setSpecification(specification(product,sku));item.setRequirements(clean(input.requirements()));
            item.setQuantity(input.quantity());item.setUnitPrice(input.unitPrice().setScale(2));item.setLineAmount(input.unitPrice().multiply(BigDecimal.valueOf(input.quantity())).setScale(2));
            total=total.add(item.getLineAmount());items.add(item);
        }
        if(total.compareTo(MAX_AMOUNT)>0) throw new BusinessException("报价总金额超出支持范围");
        quote.setTotalAmount(total.setScale(2));return items;
    }

    private ProductVO activeSku(Long id) {
        var sku=productMapper.sku(id);if(sku==null || sku.getStatus()!=1) throw new BusinessException("请选择启用的销售SKU");
        var product=products.detail(sku.getProductId());if(!product.isAvailable()) throw new BusinessException("产品或所属分类已停用，请调整报价明细");return product;
    }
    private String specification(ProductVO product,ProductSku sku) {
        List<String> values=new ArrayList<>();
        for(var field:product.getAttributeTemplate()) if(product.getAttributes().get(field.getAttributeCode())!=null) values.add(attributeText(field,product.getAttributes().get(field.getAttributeCode())));
        for(var field:product.getSkuAttributeTemplate()) if(sku.getAttributes().get(field.getAttributeCode())!=null) values.add(attributeText(field,sku.getAttributes().get(field.getAttributeCode())));
        if(values.isEmpty() && product.getSpecifications()!=null) values.add(product.getSpecifications());
        return String.join("；",values);
    }
    private String attributeText(ProductAttributeVO field,Object value) {
        String text=value instanceof Boolean flag?(flag?"是":"否"):String.valueOf(value);
        return field.getAttributeName()+"："+text+(value instanceof Number?clean(field.getUnit()):"");
    }
    private void saveItems(Long id,List<QuotationItem> items) {for(var item:items){item.setQuotationId(id);mapper.insertItem(item);}}
    private Map<String,DataScope> scope(User user,String code) {
        var grants=access.scopes(user);return Map.of("quote",access.require(grants,code),"customer",access.require(grants,"customer:list"),"business",access.require(grants,"business:list"));
    }
    private CustomerVO customer(Long id,Map<String,DataScope> scopes,boolean lock) {
        var customer=workflow.customer(id,lock);if(customer==null) throw new BusinessException("客户不存在");
        for(var value:scopes.values()) access.check(access.customer(value,customer));return customer;
    }
    private OpportunityVO opportunity(Long id,Long customerId,boolean lock,boolean allowClosed) {
        var opportunity=lock?workflow.lockOpportunity(id):workflow.opportunity(id);
        if(opportunity==null || !opportunity.getCustomerId().equals(customerId)) throw new BusinessException("商机必须属于报价客户");
        if(!allowClosed && Set.of("WON","LOST").contains(opportunity.getStage())) throw new BusinessException("已结束商机不能继续编制或确认报价");return opportunity;
    }
    private QuotationVO quote(Long id,User user,String code,boolean lock) {
        var quote=mapper.find(id);if(quote==null) throw new BusinessException("报价不存在");
        customer(quote.getCustomerId(),scope(user,code),lock);opportunity(quote.getOpportunityId(),quote.getCustomerId(),lock,true);
        if(!lock) return quote;
        var locked=mapper.lock(id);
        if(locked==null) throw new BusinessException("报价不存在或已删除");
        return locked;
    }
    private void actions(QuotationVO quote,Map<String,DataScope> grants) {
        boolean expired=quote.getStatus().equals("SENT") && quote.getValidUntil().isBefore(LocalDate.now());
        quote.setDisplayStatus(expired?"EXPIRED":quote.getStatus());
        boolean closed=Set.of("WON","LOST").contains(quote.getOpportunityStage());
        Map<String,String> candidates=new LinkedHashMap<>();
        if(Set.of("DRAFT","VOID").contains(quote.getStatus())) candidates.put("delete","quotation:delete");
        if(!closed && quote.getStatus().equals("DRAFT")){candidates.put("edit","quotation:edit");candidates.put("send","quotation:send");}
        if(!closed && quote.getStatus().equals("SENT") && !expired){candidates.put("accept","quotation:accept");candidates.put("reject","quotation:accept");}
        boolean bound=quote.getOrderId()!=null && !"CANCELLED".equals(quote.getOrderStatus());
        if(!closed && quote.getStatus().equals("ACCEPTED") && !Boolean.TRUE.equals(quote.getIsFinal()) && quote.getBusinessOrderQuoteId()==null) candidates.put("final","quotation:final");
        if(!closed && !bound && quote.getVersionNo().equals(quote.getLatestVersionNo()) && !Set.of("DRAFT","VOID").contains(quote.getStatus())) candidates.put("revise","quotation:revise");
        if(!bound && !quote.getStatus().equals("VOID")) candidates.put("void","quotation:void");
        if(quote.getOrderId()!=null)candidates.put("orderView","order:list");
        else if(quote.getStatus().equals("ACCEPTED") && Boolean.TRUE.equals(quote.getIsFinal()) && quote.getBusinessOrderQuoteId()==null && !quote.getOpportunityStage().equals("LOST"))candidates.put("order","order:add");
        candidates.forEach((action,code)->{var range=grants.get(code);if(range!=null && range.owns(quote.getOwnerId(),quote.getOwnerDeptId()))quote.getActions().add(action);});
    }
    private void requireState(Quotation quote,String state) {if(!quote.getStatus().equals(state)) throw new BusinessException("报价状态已变化，请刷新后操作");}
    private String clean(String value) {return value==null?"":value.trim();}
}
