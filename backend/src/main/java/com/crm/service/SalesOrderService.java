package com.crm.service;

import com.crm.common.*;
import com.crm.dto.*;
import com.crm.entity.*;
import com.crm.mapper.*;
import com.crm.security.*;
import com.crm.vo.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SalesOrderService {
    private final SalesOrderMapper mapper;
    private final QuotationMapper quotations;
    private final WorkflowMapper workflow;
    private final WorkflowAccess access;
    private final ProductMapper productMapper;
    private final ProductService products;
    private final ObjectMapper json;
    private final DeliveryService deliveries;
    private static final BigDecimal MAX_AMOUNT=new BigDecimal("999999999999.99");

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Long generate(Long quotationId,User user) {
        var quote=quotations.find(quotationId);
        if(quote==null) throw new BusinessException("报价不存在");
        var customer=customer(quote.getCustomerId(),user,"order:add",true);
        var grants=access.scopes(user);
        access.check(access.customer(access.require(grants,"quotation:list"),customer));
        access.check(access.customer(access.require(grants,"business:list"),customer));
        var business=workflow.lockOpportunity(quote.getOpportunityId());
        quote=quotations.lock(quotationId);
        if(quote==null) throw new BusinessException("报价不存在或已删除");
        var existing=mapper.byQuotation(quotationId);
        if(existing!=null) return existing.getId();
        if(!"ACCEPTED".equals(quote.getStatus()) || !Boolean.TRUE.equals(quote.getIsFinal()))
            throw new BusinessException("只有客户已接受的最终报价可以生成订单");
        if("LOST".equals(business.getStage())) throw new BusinessException("已丢单商机不能生成订单");
        if(mapper.activeByOpportunity(quote.getOpportunityId())!=null) throw new BusinessException("该商机已有订单，请先处理已有订单");

        SalesOrder order=new SalesOrder();
        order.setOrderNo("SO"+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase());
        order.setQuotationId(quotationId);
        order.setCustomerId(quote.getCustomerId());
        order.setOpportunityId(quote.getOpportunityId());
        order.setCreatedBy(user.getId());
        order.setStatus("DRAFT");
        order.setTotalAmount(quote.getTotalAmount());
        order.setTaxMode(quote.getTaxMode());
        order.setPaymentTerms(quote.getPaymentTerms());
        order.setDeliveryTerms(quote.getDeliveryTerms());
        order.setFreightTerms(quote.getFreightTerms());
        order.setRemark(quote.getRemark());
        order.setRecipientName(quote.getContactName());
        order.setRecipientPhone(quote.getContactPhone().isBlank()?quote.getContactLandline():quote.getContactPhone());
        order.setShippingAddress(clean(customer.getAddress()));
        Quotation source=new Quotation();
        BeanUtils.copyProperties(quote,source);
        var sourceItems=quotations.items(quotationId);
        order.setSnapshotJson(write(new OrderSnapshot(source,sourceItems)));
        mapper.insert(order);
        for(var sourceItem:sourceItems) {
            SalesOrderItem item=new SalesOrderItem();
            BeanUtils.copyProperties(sourceItem,item);
            item.setId(null);
            item.setOrderId(order.getId());
            mapper.insertItem(item);
        }
        event(order.getId(),user,"CREATE","由最终报价生成订单草稿",null);
        quotations.event(quotationId,user.getId(),"ORDER","生成订单草稿 "+order.getOrderNo());
        return order.getId();
    }

    public PageResult<SalesOrderVO> page(User user,OrderQuery query) {
        if(query.getStatus()!=null && !query.getStatus().isBlank() && !Set.of("DRAFT","CONFIRMED","COMPLETED","CANCELLED").contains(query.getStatus()))
            throw new BusinessException("订单状态不正确");
        if(query.getDateFrom()!=null && query.getDateTo()!=null && query.getDateFrom().isAfter(query.getDateTo()))
            throw new BusinessException("开始日期不能晚于结束日期");
        var scopes=scopes(user,"order:list");
        var rows=mapper.page(scopes,query);
        var grants=access.scopes(user);
        rows.forEach(row->actions(row,grants));
        return new PageResult<>(rows,mapper.count(scopes,query));
    }

    public SalesOrderVO detail(Long id,User user) {
        var order=order(id,user,"order:list",false);
        order.setSourceSnapshot(read(order.getSnapshotJson(),OrderSnapshot.class));
        order.setItems(mapper.items(id));
        var events=mapper.events(id);
        for(var event:events) {
            if(event.getBeforeJson()!=null) event.setBeforeContent(read(event.getBeforeJson(),OrderRecord.class));
            event.setAfterContent(read(event.getAfterJson(),OrderRecord.class));
        }
        order.setEvents(events);
        actions(order,access.scopes(user));
        return order;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void update(Long id,OrderDraftRequest request,User user) {
        var order=order(id,user,"order:edit",true);
        requireState(order,"DRAFT");
        checkVersion(order,request.version());
        String before=record(id);
        var items=apply(order,request,user);
        saveContent(order,items);
        event(id,user,"EDIT","编辑订单草稿",before);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void delete(Long id,User user) {
        var order=order(id,user,"order:delete",true);
        if(!Set.of("DRAFT","CANCELLED").contains(order.getStatus())) throw new BusinessException("仅草稿或已取消的订单可以删除");
        if("DRAFT".equals(order.getStatus()) && mapper.hasDownstream(id)) throw new BusinessException("订单已有交付或发货记录，不能删除");
        if("CANCELLED".equals(order.getStatus()) && mapper.hasPostedShipments(id)) throw new BusinessException("订单仍有有效发货记录，不能删除，请先核实实际交付情况");
        String before=record(id);
        mapper.softDelete(id,user.getId());
        event(id,user,"DELETE","逻辑删除"+("CANCELLED".equals(order.getStatus())?"已取消订单":"订单草稿")+"，原明细与历史保留",before);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void confirm(Long id,User user) {
        var order=order(id,user,"order:confirm",true);
        requireState(order,"DRAFT");
        checkBusinessAccess(order,user);
        var business=workflow.lockOpportunity(order.getOpportunityId());
        var quote=quotations.lock(order.getQuotationId());
        if(!"ACCEPTED".equals(quote.getStatus()) || !Boolean.TRUE.equals(quote.getIsFinal()))
            throw new BusinessException("订单来源必须仍是客户已接受的最终报价");
        if("LOST".equals(business.getStage())) throw new BusinessException("已丢单商机不能确认订单");
        complete(order,mapper.items(id),false);
        String before=record(id);
        order.setStatus("CONFIRMED");
        order.setConfirmedAt(LocalDateTime.now());
        mapper.state(order);
        if(!"WON".equals(business.getStage())) {
            business.setStage("WON");
            business.setCloseReason("已确认销售订单 "+order.getOrderNo());
            workflow.updateOpportunity(business);
        }
        deliveries.register(id,user);
        event(id,user,"CONFIRM","客户正式下单确认，商机标记为已赢单",before);
        quotations.event(quote.getId(),user.getId(),"CONFIRM_ORDER","确认订单 "+order.getOrderNo()+"；正式下单依据保存在订单详情");
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void change(Long id,OrderChangeRequest request,User user) {
        var order=order(id,user,"order:change",true);
        requireState(order,"CONFIRMED");
        checkVersion(order,request.content().version());
        String before=record(id);
        LocalDate originalPromisedDate=order.getPromisedDate();
        var items=apply(order,request.content(),user);
        order.setConfirmationNote(request.customerConfirmation().trim());
        complete(order,items==null?mapper.items(id):items,Objects.equals(originalPromisedDate,order.getPromisedDate()));
        deliveries.checkOrderChange(id,items,false);
        saveContent(order,items);
        event(id,user,"CHANGE",request.reason().trim(),before);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void cancel(Long id,ReasonRequest request,User user) {
        var order=order(id,user,"order:cancel",true);
        requireState(order,"DRAFT");
        cancel(order,request.reason(),user);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void cancelConfirmed(Long id,OrderCancelRequest request,User user) {
        var order=order(id,user,"order:cancel:confirmed",true);
        requireState(order,"CONFIRMED");
        checkVersion(order,request.version());
        checkBusinessAccess(order,user);
        deliveries.checkOrderChange(id,null,true);
        cancel(order,request.reason(),user);
        var business=workflow.lockOpportunity(order.getOpportunityId());
        if("WON".equals(business.getStage())) {
            business.setStage("NEGOTIATION");
            business.setCloseReason("");
            workflow.updateOpportunity(business);
        }
    }

    private void cancel(SalesOrder order,String reason,User user) {
        String before=record(order.getId());
        order.setStatus("CANCELLED");
        order.setCancelReason(reason.trim());
        mapper.state(order);
        event(order.getId(),user,"CANCEL",reason.trim(),before);
        quotations.event(order.getQuotationId(),user.getId(),"CANCEL_ORDER","取消订单 "+order.getOrderNo()+"；原因保存在订单详情");
    }

    // 订单内容独立于来源报价，原报价和生成时快照始终保留。
    private List<SalesOrderItem> apply(SalesOrder order,OrderDraftRequest request,User user) {
        order.setCustomerOrderNo(clean(request.customerOrderNo()));
        order.setRecipientName(clean(request.recipientName()));
        order.setRecipientPhone(clean(request.recipientPhone()));
        order.setShippingAddress(clean(request.shippingAddress()));
        order.setRequestedDate(request.requestedDate());
        order.setPromisedDate(request.promisedDate());
        order.setConfirmationNote(clean(request.confirmationNote()));
        if(request.taxMode()!=null) order.setTaxMode(request.taxMode());
        if(request.paymentTerms()!=null) order.setPaymentTerms(request.paymentTerms().trim());
        if(request.deliveryTerms()!=null) order.setDeliveryTerms(request.deliveryTerms().trim());
        if(request.freightTerms()!=null) order.setFreightTerms(request.freightTerms().trim());
        if(request.remark()!=null) order.setRemark(request.remark().trim());
        if(request.items()==null) return null;

        access.require(access.scopes(user),"product:list");
        var existing=mapper.items(order.getId());
        List<SalesOrderItem> items=new ArrayList<>();
        BigDecimal total=BigDecimal.ZERO;
        Set<Long> used=new HashSet<>();
        for(var input:request.items()) {
            SalesOrderItem item=new SalesOrderItem();
            var saved=existing.stream().filter(row->input.id()!=null?row.getId().equals(input.id()):row.getSkuId().equals(input.skuId()) && !used.contains(row.getId())).findFirst();
            if(input.id()!=null && saved.isEmpty()) throw new BusinessException("订单产品明细不属于当前订单");
            if(saved.isPresent() && !used.add(saved.get().getId())) throw new BusinessException("订单产品行不能重复");
            if(saved.isPresent()) BeanUtils.copyProperties(saved.get(),item);
            else populateSku(item,input.skuId());
            item.setId(saved.map(SalesOrderItem::getId).orElse(null));
            if(saved.isPresent() && !saved.get().getSkuId().equals(input.skuId())) populateSku(item,input.skuId());
            item.setOrderId(order.getId());
            item.setSortNo(saved.map(SalesOrderItem::getSortNo).orElse(null));
            item.setQuantity(input.quantity());
            item.setUnitPrice(input.unitPrice().setScale(2));
            item.setLineAmount(input.unitPrice().multiply(BigDecimal.valueOf(input.quantity())).setScale(2));
            item.setRequirements(clean(input.requirements()));
            total=total.add(item.getLineAmount());
            items.add(item);
        }
        if(total.compareTo(MAX_AMOUNT)>0) throw new BusinessException("订单总金额超出支持范围");
        order.setTotalAmount(total);
        return items;
    }

    private void saveContent(SalesOrder order,List<SalesOrderItem> items) {
        mapper.updateDraft(order);
        if(items==null) return;
        var previous=mapper.items(order.getId());
        for(var old:previous) if(items.stream().noneMatch(item->Objects.equals(item.getId(),old.getId()))) mapper.retireItem(old.getId());
        int sort=mapper.maxSort(order.getId());
        for(var item:items) {
            if(item.getId()==null) {item.setSortNo(++sort);mapper.insertItem(item);}
            else mapper.updateItem(item);
        }
    }

    public List<QuotationSkuVO> skus(Long id,User user,String keyword) {
        var current=mapper.find(id);
        if(current==null) throw new BusinessException("订单不存在");
        String code="DRAFT".equals(current.getStatus())?"order:edit":"order:change";
        order(id,user,code,false);
        access.require(access.scopes(user),"product:list");
        List<QuotationSkuVO> rows=new ArrayList<>();
        for(Long skuId:quotations.skuIds(keyword)) {
            var sku=productMapper.sku(skuId);
            var product=products.detail(sku.getProductId());
            if(!product.isAvailable()) continue;
            var actual=product.getSkus().stream().filter(s->s.getId().equals(skuId)).findFirst().orElseThrow();
            rows.add(new QuotationSkuVO(skuId,sku.getSkuCode(),product.getProductName(),product.getModelCode(),product.getUnit(),sku.getSalePrice(),specification(product,actual)));
            if(rows.size()==50) break;
        }
        return rows;
    }

    private void populateSku(SalesOrderItem item,Long skuId) {
        var sku=productMapper.sku(skuId);
        if(sku==null || sku.getStatus()!=1) throw new BusinessException("新增产品请选择启用的销售SKU");
        var product=products.detail(sku.getProductId());
        if(!product.isAvailable()) throw new BusinessException("产品或分类已停用");
        var actual=product.getSkus().stream().filter(s->s.getId().equals(skuId)).findFirst().orElseThrow();
        item.setSkuId(skuId);
        item.setProductName(product.getProductName());
        item.setModelCode(product.getModelCode());
        item.setSkuCode(sku.getSkuCode());
        item.setUnit(product.getUnit());
        item.setSpecification(specification(product,actual));
    }

    private String specification(ProductVO product,ProductSku sku) {
        List<String> fields=new ArrayList<>();
        for(var field:product.getAttributeTemplate()) if(product.getAttributes().get(field.getAttributeCode())!=null)
            fields.add(attribute(field,product.getAttributes().get(field.getAttributeCode())));
        for(var field:product.getSkuAttributeTemplate()) if(sku.getAttributes().get(field.getAttributeCode())!=null)
            fields.add(attribute(field,sku.getAttributes().get(field.getAttributeCode())));
        return fields.isEmpty()?clean(product.getSpecifications()):String.join("；",fields);
    }

    private String attribute(ProductAttributeVO field,Object value) {
        String text=value instanceof Boolean flag?(flag?"是":"否"):String.valueOf(value);
        return field.getAttributeName()+"："+text+(value instanceof Number?clean(field.getUnit()):"");
    }

    private void complete(SalesOrder order,List<SalesOrderItem> items,boolean keepOriginalDate) {
        List<String> missing=new ArrayList<>();
        if(order.getRecipientName().isBlank()) missing.add("收货人");
        if(order.getRecipientPhone().isBlank()) missing.add("收货电话");
        if(order.getShippingAddress().isBlank()) missing.add("收货地址");
        if(order.getPromisedDate()==null) missing.add("公司承诺交期");
        if(order.getConfirmationNote().isBlank()) missing.add("正式下单依据");
        if(order.getPaymentTerms().isBlank()) missing.add("付款条件");
        if(order.getDeliveryTerms().isBlank()) missing.add("交期约定");
        if(!missing.isEmpty()) throw new BusinessException("请补齐："+String.join("、",missing));
        if(!keepOriginalDate && order.getPromisedDate().isBefore(LocalDate.now())) throw new BusinessException("承诺交期不能早于今天");
        if(items.isEmpty()) throw new BusinessException("订单至少需要一项产品");
    }

    private SalesOrderVO order(Long id,User user,String code,boolean lock) {
        var order=mapper.find(id);
        if(order==null) throw new BusinessException("订单不存在");
        customer(order.getCustomerId(),user,code,lock);
        if(!lock) return order;
        workflow.lockOpportunity(order.getOpportunityId());
        quotations.lock(order.getQuotationId());
        var locked=mapper.lock(id);
        if(locked==null) throw new BusinessException("订单不存在或已删除");
        return locked;
    }

    private Map<String,DataScope> scopes(User user,String code) {
        var grants=access.scopes(user);
        return Map.of("order",access.require(grants,code),"customer",access.require(grants,"customer:list"));
    }

    private CustomerVO customer(Long id,User user,String code,boolean lock) {
        var customer=workflow.customer(id,lock);
        if(customer==null) throw new BusinessException("客户不存在");
        for(var scope:scopes(user,code).values()) access.check(access.customer(scope,customer));
        return customer;
    }

    private void checkBusinessAccess(SalesOrder order,User user) {
        var customer=workflow.customer(order.getCustomerId(),true);
        access.check(access.customer(access.require(access.scopes(user),"business:list"),customer));
    }

    private void actions(SalesOrderVO order,Map<String,DataScope> grants) {
        var delivery=deliveries.summary(order.getId());
        if(delivery!=null) {order.setDeliveryStatus(delivery.getStatus());order.setExecutionStarted(Boolean.TRUE.equals(delivery.getStarted()));}
        var candidates="DRAFT".equals(order.getStatus())?List.of("edit","confirm","cancel","delete")
                :"CONFIRMED".equals(order.getStatus())?List.of("change","cancel:confirmed")
                :"CANCELLED".equals(order.getStatus())?List.of("delete"):List.<String>of();
        for(String action:candidates) {
            if(action.equals("cancel:confirmed") && delivery!=null && Set.of("PARTIAL","SHIPPED").contains(delivery.getStatus())) continue;
            var scope=grants.get("order:"+action);
            if(scope!=null && scope.owns(order.getOwnerId(),order.getOwnerDeptId())) order.getActions().add(action);
        }
    }

    private void requireState(SalesOrder order,String state) {
        if(!state.equals(order.getStatus())) throw new BusinessException("订单状态不允许此操作，请刷新；已确认订单请使用专门的变更或取消入口");
    }

    private void checkVersion(SalesOrder order,Integer version) {
        if(!order.getVersion().equals(version)) throw new BusinessException("订单已被其他操作更新，请刷新后重试");
    }

    private String record(Long id) {
        SalesOrder header=new SalesOrder();
        BeanUtils.copyProperties(mapper.findIncludingDeleted(id),header);
        return write(new OrderRecord(header,mapper.items(id)));
    }

    private void event(Long id,User user,String action,String note,String before) {
        OrderEventVO event=new OrderEventVO();
        event.setOrderId(id);
        event.setActorId(user.getId());
        event.setAction(action);
        event.setNote(note);
        event.setBeforeJson(before);
        event.setAfterJson(record(id));
        mapper.event(event);
    }

    private String write(Object value) {
        try {return json.writeValueAsString(value);}
        catch(JsonProcessingException e) {throw new IllegalStateException(e);}
    }

    private <T> T read(String value,Class<T> type) {
        try {return json.readValue(value,type);}
        catch(JsonProcessingException e) {throw new IllegalStateException(e);}
    }

    private String clean(String value) {return value==null?"":value.trim();}
}
