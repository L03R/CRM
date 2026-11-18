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
import org.springframework.transaction.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.*;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AfterSaleService {
    private final AfterSaleMapper mapper;
    private final WorkflowMapper workflow;
    private final ContactMapper contacts;
    private final SalesOrderMapper orders;
    private final DeliveryMapper delivery;
    private final WorkflowAccess access;
    private static final Set<String> TYPES=Set.of("QUALITY","TRANSPORT","WRONG_MISSING","CONSULTATION","OTHER");

    public PageResult<AfterSaleVO> page(User user,AfterSaleQuery query) {
        if(query.getStatus()!=null && !query.getStatus().isBlank() && !Set.of("PENDING","TRANSFERRED","CLOSED").contains(query.getStatus())) throw new BusinessException("售后状态不正确");
        if(query.getIssueType()!=null && !query.getIssueType().isBlank() && !TYPES.contains(query.getIssueType())) throw new BusinessException("问题类型不正确");
        if(query.getDateFrom()!=null && query.getDateTo()!=null && query.getDateFrom().isAfter(query.getDateTo())) throw new BusinessException("开始日期不能晚于结束日期");
        var grants=access.scopes(user);var scope=access.require(grants,"aftersale:list");
        var rows=mapper.page(scope,grants.get("customer:list"),query);rows.forEach(row->actions(row,user));
        return new PageResult<>(rows,mapper.count(scope,grants.get("customer:list"),query));
    }
    public List<Map<String,Object>> owners(User user) {
        var grants=access.scopes(user);return mapper.owners(access.require(grants,"aftersale:list"),grants.get("customer:list"));
    }
    public AfterSaleVO detail(Long id,User user) {
        var record=required(id,user,"aftersale:list",false);
        record.setItems(mapper.items(id));record.setEvents(mapper.events(id));record.setImages(mapper.images(id));actions(record,user);return record;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Long create(AfterSaleRequest request,User user) {
        customer(request.customerId(),user,"aftersale:add",true);
        AfterSale record=new AfterSale();BeanUtils.copyProperties(request,record);
        record.setCaseNo("AS"+LocalDate.now().toString().replace("-","")+UUID.randomUUID().toString().substring(0,8).toUpperCase());
        record.setTitle(request.title().trim());record.setDescription(request.description().trim());record.setExpectation(clean(request.expectation()));
        record.setReporterName(request.reporterName().trim());record.setReporterContact(request.reporterContact().trim());
        record.setOwnerId(user.getId());record.setCreatedBy(user.getId());
        var items=source(record,request.source());mapper.insert(record);saveItems(record.getId(),items);
        event(record.getId(),user,"CREATE","登记客户反馈："+record.getDescription(),request.reportedAt());
        return record.getId();
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void source(Long id,AfterSaleSourceUpdate request,User user) {
        var record=required(id,user,"aftersale:source",true);open(record);version(record,request.version());
        String before=sourceSummary(record,mapper.items(id));
        var items=source(record,request.source());
        mapper.update(record);mapper.deleteItems(id);saveItems(id,items);
        event(id,user,"SOURCE",request.note().trim()+"；核实前："+before+"；核实后："+sourceSummary(record,items),LocalDateTime.now());
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void transfer(Long id,AfterSaleTransferRequest request,User user) {
        var record=required(id,user,"aftersale:transfer",true);open(record);version(record,request.version());
        User helper=request.helperId()==null?null:candidate(request.helperId(),"aftersale:assist");
        record.setHandlerName(request.handlerName().trim());record.setHandlerContact(request.handlerContact().trim());record.setHelperId(request.helperId());
        record.setNextFollowAt(request.nextFollowAt());record.setStatus("TRANSFERRED");mapper.update(record);
        event(id,user,"TRANSFER","转交给："+record.getHandlerName()+"；联系方式："+record.getHandlerContact()+"；内部协助："+(helper==null?"无":helper.getRealName())+"；"+request.note().trim(),LocalDateTime.now());
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void follow(Long id,AfterSaleFollowRequest request,User user) {
        var record=required(id,user,"aftersale:follow",true);open(record);version(record,request.version());
        record.setLatestFeedback(request.content().trim());record.setNextFollowAt(request.nextFollowAt());mapper.update(record);
        event(id,user,"FOLLOW",request.content().trim(),request.occurredAt());
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void assist(Long id,AfterSaleFollowRequest request,User user) {
        var record=required(id,user,"aftersale:assist",true);open(record);version(record,request.version());
        access.check(Objects.equals(record.getHelperId(),user.getId()));
        record.setLatestFeedback(request.content().trim());mapper.update(record);
        // 协助反馈不改变销售负责人的跟进计划。
        event(id,user,"ASSIST",request.content().trim(),request.occurredAt());
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void assign(Long id,AfterSaleAssignRequest request,User user) {
        var record=required(id,user,"aftersale:assign",true);open(record);version(record,request.version());
        var owner=candidate(request.ownerId(),"aftersale:follow");
        String before=record.getOwnerName();record.setOwnerId(owner.getId());mapper.update(record);
        event(id,user,"ASSIGN","售后跟进负责人："+before+" → "+owner.getRealName()+"；"+request.note().trim(),LocalDateTime.now());
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void close(Long id,AfterSaleCloseRequest request,User user) {
        var record=required(id,user,"aftersale:close",true);open(record);version(record,request.version());
        record.setStatus("CLOSED");record.setResolutionType(request.resolutionType());record.setResultNote(request.resultNote().trim());
        record.setCustomerFeedback(request.customerFeedback().trim());record.setExternalNo(clean(request.externalNo()));record.setClosedAt(LocalDateTime.now());record.setNextFollowAt(null);mapper.update(record);
        event(id,user,"CLOSE","处理方式："+request.resolutionType()+"；结果："+record.getResultNote()+"；客户反馈/结案依据："+record.getCustomerFeedback()+"；外部单号："+record.getExternalNo(),LocalDateTime.now());
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void reopen(Long id,AfterSaleReopenRequest request,User user) {
        var record=required(id,user,"aftersale:reopen",true);version(record,request.version());
        if(!"CLOSED".equals(record.getStatus())) throw new BusinessException("仅已结束记录可以重新开启");
        record.setStatus(record.getHandlerName().isBlank()?"PENDING":"TRANSFERRED");record.setNextFollowAt(request.nextFollowAt());record.setClosedAt(null);
        record.setResolutionType(null);record.setResultNote("");record.setCustomerFeedback("");record.setExternalNo("");mapper.update(record);
        event(id,user,"REOPEN",request.note().trim(),LocalDateTime.now());
    }

    public List<ContactCustomerVO> customers(User user,String keyword) {
        var grants=access.scopes(user);
        return contacts.customers(access.require(grants,"aftersale:add"),access.require(grants,"customer:list"),keyword);
    }

    public Map<String,Object> sources(User user,Long customerId,Long orderId,Long shipmentId,Long caseId) {
        AfterSale context=null;
        if(caseId==null) customer(customerId,user,"aftersale:add",false);
        else {context=required(caseId,user,"aftersale:source",false);if(!context.getCustomerId().equals(customerId)) throw new BusinessException("售后所属客户不能修改");}
        AfterSale candidate=new AfterSale();candidate.setCustomerId(customerId);candidate.setOrderId(context==null?null:context.getOrderId());candidate.setShipmentId(context==null?null:context.getShipmentId());
        validateOrder(candidate,orderId,shipmentId);
        List<DeliveryLineVO> lines=orderId==null?List.of():delivery.lines(orderId);
        if(shipmentId!=null) {
            var shipped=delivery.shipmentItems(shipmentId);
            List<DeliveryLineVO> snapshots=new ArrayList<>();
            for(var item:shipped) {var row=new DeliveryLineVO();row.setId(item.getOrderItemId());row.setProductName(item.getProductName());row.setSkuCode(item.getSkuCode());row.setSpecification(item.getSpecification());row.setQuantity(item.getQuantity());snapshots.add(row);}
            lines=snapshots;
        }
        var shipments=orderId==null?List.of():delivery.shipments(orderId).stream().filter(h->"POSTED".equals(h.getStatus())).map(h->Map.of("id",h.getId(),"shipmentNo",h.getShipmentNo())).toList();
        return Map.of("orders",mapper.orders(customerId),"shipments",shipments,"lines",lines,"contacts",mapper.contacts(customerId));
    }

    public Map<String,List<Map<String,Object>>> people(Long id,User user) {
        var record=mapper.find(id);if(record==null) throw new BusinessException("售后记录不存在");
        boolean assign=allowed(record,user,"aftersale:assign"),transfer=allowed(record,user,"aftersale:transfer");access.check(assign || transfer);
        List<Map<String,Object>> owners=new ArrayList<>(),helpers=new ArrayList<>();
        for(var employee:delivery.users()) {
            var grants=access.scopes(employee);if(!grants.containsKey("aftersale:list")) continue;
            var option=Map.<String,Object>of("id",employee.getId(),"name",employee.getRealName()+" / "+employee.getEmployeeNo());
            if(assign && grants.containsKey("aftersale:follow")) owners.add(option);
            if(transfer && grants.containsKey("aftersale:assist")) helpers.add(option);
        }
        return Map.of("owners",owners,"helpers",helpers);
    }

    private List<AfterSaleItem> source(AfterSale record,AfterSaleSourceRequest request) {
        validateOrder(record,request.orderId(),request.shipmentId());
        if(request.contactId()!=null) {
            var contact=contacts.find(request.contactId());
            if(contact==null || !contact.getCustomerId().equals(record.getCustomerId())) throw new BusinessException("联系人必须属于当前客户");
        }
        List<SalesOrderItem> orderItems=request.orderId()==null?List.of():orders.items(request.orderId());
        List<ShipmentItem> shipmentItems=request.shipmentId()==null?List.of():delivery.shipmentItems(request.shipmentId());
        boolean sameSource=record.getId()!=null && Objects.equals(record.getOrderId(),request.orderId()) && Objects.equals(record.getShipmentId(),request.shipmentId());
        var previous=sameSource?mapper.items(record.getId()):List.<AfterSaleItem>of();
        List<AfterSaleItem> result=new ArrayList<>();Set<Long> ids=new HashSet<>();
        for(var input:request.items()) {
            AfterSaleItem item=new AfterSaleItem();item.setQuantity(input.quantity());item.setOrderItemId(input.orderItemId());
            if(request.orderId()!=null) {
                if(input.orderItemId()==null || !ids.add(input.orderItemId())) throw new BusinessException("请选择不重复的订单产品行");
                var line=orderItems.stream().filter(i->i.getId().equals(input.orderItemId())).findFirst();
                var saved=previous.stream().filter(i->Objects.equals(i.getOrderItemId(),input.orderItemId())).findFirst();
                if(line.isEmpty() && saved.isEmpty()) throw new BusinessException("产品明细不属于当前订单");
                if(saved.isPresent()) {
                    item.setProductName(saved.get().getProductName());item.setSkuCode(saved.get().getSkuCode());item.setSpecification(saved.get().getSpecification());
                } else {item.setProductName(line.get().getProductName());item.setSkuCode(line.get().getSkuCode());item.setSpecification(line.get().getSpecification());}
                int limit=line.filter(i->Objects.equals(i.getSkuCode(),item.getSkuCode())).map(SalesOrderItem::getQuantity).orElseGet(()->saved.orElseThrow().getQuantity());
                if(request.shipmentId()!=null) {
                    var sent=shipmentItems.stream().filter(i->i.getOrderItemId().equals(input.orderItemId())).findFirst().orElseThrow(()->new BusinessException("产品明细不属于所选发货记录"));
                    item.setProductName(sent.getProductName());item.setSkuCode(sent.getSkuCode());item.setSpecification(sent.getSpecification());limit=sent.getQuantity();
                }
                if(input.quantity()>limit) throw new BusinessException("本次反馈数量不能超过所选来源产品数量；同一产品允许多次登记售后");
            } else {
                if(input.orderItemId()!=null) throw new BusinessException("尚未关联订单，不能指定订单产品行");
                if(clean(input.productName()).isBlank()) throw new BusinessException("请填写待核实的产品名称或型号");
                item.setProductName(input.productName().trim());item.setSkuCode(clean(input.skuCode()));item.setSpecification(clean(input.specification()));
            }
            result.add(item);
        }
        record.setOrderId(request.orderId());record.setShipmentId(request.shipmentId());record.setContactId(request.contactId());record.setSerialNo(clean(request.serialNo()));
        return result;
    }

    private void validateOrder(AfterSale record,Long orderId,Long shipmentId) {
        if(orderId==null) {if(shipmentId!=null) throw new BusinessException("关联发货记录前请先选择订单");return;}
        var order=orders.findIncludingDeleted(orderId);
        if(order==null || !order.getCustomerId().equals(record.getCustomerId())) throw new BusinessException("订单必须属于当前客户");
        if(!Objects.equals(record.getOrderId(),orderId) && (Boolean.TRUE.equals(order.getDeleted()) || !Set.of("CONFIRMED","COMPLETED").contains(order.getStatus()))) throw new BusinessException("请选择未删除的已确认或已完成订单");
        if(shipmentId!=null) {
            var shipment=delivery.shipment(shipmentId);
            if(shipment==null || !shipment.getOrderId().equals(orderId) || (!"POSTED".equals(shipment.getStatus()) && !Objects.equals(record.getShipmentId(),shipmentId))) throw new BusinessException("请选择该订单的有效发货记录");
        }
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Long upload(Long id,MultipartFile file,User user) throws IOException {
        var record=mapper.find(id);if(record==null) throw new BusinessException("售后记录不存在");
        String code=allowed(record,user,"aftersale:follow")?"aftersale:follow":"aftersale:assist";
        record=required(id,user,code,true);open(record);
        if(code.equals("aftersale:assist")) access.check(Objects.equals(record.getHelperId(),user.getId()));
        if(file.isEmpty() || file.getSize()>5*1024*1024) throw new BusinessException("图片不能为空且不能超过5MB");
        if(mapper.images(id).size()>=8) throw new BusinessException("每条售后记录最多8张图片");
        byte[] bytes=file.getBytes();String mime;
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext()) throw new BusinessException("仅支持有效的PNG或JPEG图片");
            var reader=readers.next();
            try {reader.setInput(input);String format=reader.getFormatName().toLowerCase();if(!Set.of("png","jpeg","jpg").contains(format)) throw new BusinessException("仅支持PNG或JPEG图片");
                if(reader.getWidth(0)>8000 || reader.getHeight(0)>8000) throw new BusinessException("图片尺寸不能超过8000像素");mime=format.equals("png")?"image/png":"image/jpeg";
            } finally {reader.dispose();}
        } catch(IOException ex) {throw new BusinessException("图片损坏，请重新选择有效的PNG或JPEG图片");}
        AfterSaleImage image=new AfterSaleImage();image.setCaseId(id);image.setActorId(user.getId());image.setFilename(clean(file.getOriginalFilename()).substring(0,Math.min(clean(file.getOriginalFilename()).length(),128)));image.setMimeType(mime);image.setImageData(bytes);mapper.insertImage(image);
        event(id,user,"IMAGE","补充问题图片凭证："+image.getFilename(),LocalDateTime.now());return image.getId();
    }
    public AfterSaleImage image(Long caseId,Long id,User user) {
        required(caseId,user,"aftersale:list",false);var image=mapper.image(caseId,id);if(image==null) throw new BusinessException("图片不存在");return image;
    }

    private AfterSaleVO required(Long id,User user,String code,boolean lock) {
        var record=mapper.find(id);if(record==null) throw new BusinessException("售后记录不存在");
        if(lock) {workflow.customer(record.getCustomerId(),true);record=mapper.lock(id);}
        access.check(allowed(record,user,code));return record;
    }
    private boolean allowed(AfterSaleVO record,User user,String code) {
        var grants=access.scopes(user);var scope=grants.get(code);if(scope==null) return false;
        if(Objects.equals(record.getOwnerId(),user.getId()) || Objects.equals(record.getHelperId(),user.getId())) return true;
        var read=grants.get("customer:list");return read!=null && scope.owns(record.getCustomerOwnerId(),record.getCustomerOwnerDeptId()) && read.owns(record.getCustomerOwnerId(),record.getCustomerOwnerDeptId());
    }
    private void customer(Long id,User user,String code,boolean lock) {
        var customer=workflow.customer(id,lock);if(customer==null) throw new BusinessException("客户不存在");
        var grants=access.scopes(user);access.check(access.customer(access.require(grants,code),customer));access.check(access.customer(access.require(grants,"customer:list"),customer));
    }
    private User candidate(Long id,String code) {
        var employee=delivery.users().stream().filter(u->u.getId().equals(id)).findFirst().orElseThrow(()->new BusinessException("请选择启用的在职员工"));
        var grants=access.scopes(employee);if(!grants.containsKey(code) || !grants.containsKey("aftersale:list")) throw new BusinessException("所选人员没有相应售后职责权限");return employee;
    }
    private void actions(AfterSaleVO record,User user) {
        for(String action:List.of("source","transfer","follow","assign","close","reopen","assist")) {
            if("CLOSED".equals(record.getStatus())!=action.equals("reopen")) continue;
            if(action.equals("assist") && !Objects.equals(record.getHelperId(),user.getId())) continue;
            if(allowed(record,user,"aftersale:"+action)) record.getActions().add(action);
        }
    }
    private void open(AfterSale record) {if("CLOSED".equals(record.getStatus())) throw new BusinessException("记录已结束，经理重新开启后才能继续维护");}
    private void version(AfterSale record,Integer version) {if(!record.getVersion().equals(version)) throw new BusinessException("售后记录已更新，请刷新后重试");}
    private void saveItems(Long id,List<AfterSaleItem> items) {for(var item:items) {item.setCaseId(id);mapper.insertItem(item);}}
    private void event(Long id,User user,String action,String content,LocalDateTime occurredAt) {mapper.event(id,user.getId(),action,content,occurredAt);}
    private String clean(String value) {return value==null?"":value.trim();}
    private String sourceSummary(AfterSale record,List<AfterSaleItem> items) {return "订单="+record.getOrderId()+"，发货="+record.getShipmentId()+"，序列/批次="+record.getSerialNo()+"，产品="+items.stream().map(i->i.getProductName()+"/"+i.getSkuCode()+"×"+i.getQuantity()).toList();}
}
