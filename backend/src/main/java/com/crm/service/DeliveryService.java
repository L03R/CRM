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
import org.springframework.transaction.annotation.Isolation;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DeliveryService {
    private final DeliveryMapper mapper;
    private final SalesOrderMapper orders;
    private final WorkflowMapper workflow;
    private final QuotationMapper quotes;
    private final WorkflowAccess access;

    public PageResult<DeliveryVO> page(User user,DeliveryQuery query) {
        if(query.getStatus()!=null && !query.getStatus().isBlank() && !Set.of("PENDING","PRODUCTION","STOCKING","PARTIAL","SHIPPED","COMPLETED","CANCELLED").contains(query.getStatus())) throw new BusinessException("交付状态不正确");
        var grants=access.scopes(user);
        var scope=access.require(grants,"delivery:progress:list");
        var rows=mapper.page(scope,grants.get("customer:list"),query);
        rows.forEach(row->actions(row,user,grants));
        return new PageResult<>(rows,mapper.count(scope,grants.get("customer:list"),query));
    }

    public DeliveryVO detail(Long id,User user) {
        var delivery=authorized(id,user,"delivery:progress:list",false);
        delivery.setItems(mapper.lines(id));
        delivery.setEvents(mapper.events(id));
        // 发货页的授权单独检查，避免借交付查看权限获取发货数据。
        if(allowed(delivery,user,"shipment:list")) delivery.setShipments(mapper.shipments(id));
        actions(delivery,user,access.scopes(user));
        return delivery;
    }

    public void register(Long id,User user) {
        mapper.ensure(id);
        mapper.event(id,user.getId(),"CREATE","正式订单进入交付待安排列表");
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void assign(Long id,DeliveryAssignRequest request,User user) {
        var delivery=authorized(id,user,"delivery:assign",true);
        active(delivery);version(delivery,request.version());
        var manager=candidate(request.managerId(),"delivery:assign");
        if(request.productionId()!=null) candidate(request.productionId(),"delivery:production");
        if(request.warehouseId()!=null) candidate(request.warehouseId(),"delivery:stock");
        if(Boolean.TRUE.equals(delivery.getStarted()) && delivery.getProductionId()!=null && request.productionId()==null) throw new BusinessException("生产已执行，不能直接清空生产负责人");
        if(Boolean.TRUE.equals(delivery.getStarted()) && delivery.getWarehouseId()!=null && request.warehouseId()==null) throw new BusinessException("备货已执行，不能直接清空仓储负责人");
        delivery.setManagerId(request.managerId());delivery.setProductionId(request.productionId());delivery.setWarehouseId(request.warehouseId());
        delivery.setProductionAck(null);delivery.setWarehouseAck(null);
        delivery.setExpectedDate(request.expectedDate());delivery.setExceptionNote(clean(request.exceptionNote()));
        mapper.update(delivery);
        mapper.event(id,user.getId(),"ASSIGN","交付对接人："+manager.getRealName()+"；生产负责人："+personName(request.productionId())+"；仓储负责人："+personName(request.warehouseId())+"；预计发货："+request.expectedDate()+"；"+clean(request.exceptionNote()));
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void progress(Long id,DeliveryProgressRequest request,User user) {
        String code=request.preparation().equals("PRODUCTION")?"delivery:production":"delivery:stock";
        var delivery=authorized(id,user,code,true);
        active(delivery);version(delivery,request.version());
        if(delivery.getManagerId()==null) throw new BusinessException("请先指定销售经理作为交付对接人");
        Long assigned=request.preparation().equals("PRODUCTION")?delivery.getProductionId():delivery.getWarehouseId();
        if(assigned==null) throw new BusinessException("请先指定对应的生产或仓储负责人");
        access.check(Objects.equals(user.getId(),assigned) || manager(delivery,user));
        if(delivery.getStatus().equals("SHIPPED")) throw new BusinessException("订单已全部发货，无需更新备货生产状态");
        delivery.setPreparation(request.preparation());delivery.setStarted(true);delivery.setExpectedDate(request.expectedDate());delivery.setExceptionNote(clean(request.exceptionNote()));
        mapper.update(delivery);
        mapper.event(id,user.getId(),"PROGRESS","执行状态："+(request.preparation().equals("PRODUCTION")?"生产中":"备货中")+"，预计发货："+request.expectedDate()+"；"+clean(request.exceptionNote()));
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void acknowledge(Long id,DeliveryAckRequest request,User user) {
        String code=request.kind().equals("PRODUCTION")?"delivery:production":"delivery:stock";
        var delivery=authorized(id,user,code,true);active(delivery);
        if(!delivery.getOrderVersion().equals(request.orderVersion())) throw new BusinessException("订单内容已变更，请刷新后重新确认执行影响");
        Long assigned=request.kind().equals("PRODUCTION")?delivery.getProductionId():delivery.getWarehouseId();
        access.check(Objects.equals(assigned,user.getId()));
        if(request.kind().equals("PRODUCTION")) delivery.setProductionAck(request.orderVersion());else delivery.setWarehouseAck(request.orderVersion());
        mapper.update(delivery);
        mapper.event(id,user.getId(),"ACK","已确认当前订单变更/取消对"+(request.kind().equals("PRODUCTION")?"生产":"备货")+"的执行影响："+request.note().trim());
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void complete(Long id,DeliveryCompleteRequest request,User user) {
        var delivery=authorized(id,user,"delivery:complete",true);active(delivery);version(delivery,request.version());
        if(!delivery.getStatus().equals("SHIPPED")) throw new BusinessException("所有产品全部发货后才能确认客户收货、完成交付");
        var order=orders.lock(id);order.setStatus("COMPLETED");orders.state(order);
        mapper.update(delivery);
        mapper.event(id,user.getId(),"COMPLETE",request.note().trim());
    }

    public Map<String,List<Map<String,Object>>> options(Long id,User user) {
        authorized(id,user,"delivery:assign",false);
        Map<String,List<Map<String,Object>>> result=new LinkedHashMap<>();
        for(String kind:List.of("manager","production","warehouse")) result.put(kind,new ArrayList<>());
        for(var employee:mapper.users()) {
            var grants=access.scopes(employee);
            for(var entry:Map.of("manager","delivery:assign","production","delivery:production","warehouse","delivery:stock").entrySet())
                if(grants.containsKey(entry.getValue()) && grants.containsKey("delivery:progress:list")) result.get(entry.getKey()).add(Map.of("id",employee.getId(),"name",employee.getRealName()+" / "+employee.getEmployeeNo()));
        }
        return result;
    }

    public PageResult<Shipment> shipments(User user,DeliveryQuery query) {
        if(query.getStatus()!=null && !query.getStatus().isBlank() && !Set.of("POSTED","VOID").contains(query.getStatus())) throw new BusinessException("发货记录状态不正确");
        var grants=access.scopes(user);var scope=access.require(grants,"shipment:list");
        return new PageResult<>(mapper.shipmentPage(scope,grants.get("customer:list"),query),mapper.shipmentCount(scope,grants.get("customer:list"),query));
    }

    public Shipment shipment(Long id,User user) {
        var shipment=mapper.shipment(id);if(shipment==null) throw new BusinessException("发货记录不存在");
        authorized(shipment.getOrderId(),user,"shipment:list",false);
        shipment.setItems(mapper.shipmentItems(id));return shipment;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Long ship(ShipmentRequest request,User user) {
        var delivery=authorized(request.orderId(),user,"shipment:add",true);active(delivery);
        access.check(Objects.equals(delivery.getWarehouseId(),user.getId()) || manager(delivery,user));
        var existing=mapper.byRequest(request.orderId(),request.requestKey());if(existing!=null) return existing.getId();
        if(!delivery.getOrderVersion().equals(request.orderVersion())) throw new BusinessException("订单已变更，请刷新发货明细");
        if(!delivery.getPreparation().equals("STOCKING") || delivery.getWarehouseId()==null || delivery.getManagerId()==null) throw new BusinessException("请先分配负责人并进入备货阶段，再登记实际发货");
        var lines=mapper.lines(request.orderId());Set<Long> ids=new HashSet<>();List<ShipmentItem> items=new ArrayList<>();
        for(var input:request.items()) {
            if(!ids.add(input.orderItemId())) throw new BusinessException("同一产品明细不能重复填写");
            var source=lines.stream().filter(line->line.getId().equals(input.orderItemId())).findFirst().orElseThrow(()->new BusinessException("发货明细必须属于当前订单"));
            if(input.quantity()>source.getQuantity()-source.getShippedQuantity()) throw new BusinessException("本次发货数量超过该产品剩余待发数量");
            ShipmentItem item=new ShipmentItem();BeanUtils.copyProperties(source,item);item.setId(null);item.setOrderItemId(source.getId());item.setOrderId(request.orderId());item.setQuantity(input.quantity());items.add(item);
        }
        Shipment shipment=new Shipment();BeanUtils.copyProperties(request,shipment);
        shipment.setShipmentNo("SH"+LocalDate.now().toString().replace("-","")+UUID.randomUUID().toString().substring(0,8).toUpperCase());shipment.setActorId(user.getId());shipment.setRemark(clean(request.remark()));
        mapper.insertShipment(shipment);
        for(var item:items) {item.setShipmentId(shipment.getId());mapper.insertShipmentItem(item);}
        mapper.update(delivery);
        mapper.event(request.orderId(),user.getId(),"SHIP","登记实际发货 "+shipment.getShipmentNo()+"，物流="+shipment.getCarrier()+" / "+shipment.getTrackingNo());
        return shipment.getId();
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void voidShipment(Long id,ReasonRequest request,User user) {
        var shipment=mapper.shipment(id);if(shipment==null) throw new BusinessException("发货记录不存在");
        var delivery=authorized(shipment.getOrderId(),user,"shipment:void",true);active(delivery);
        shipment=mapper.shipment(id);
        if(!shipment.getStatus().equals("POSTED")) throw new BusinessException("该记录已更正作废");
        mapper.voidShipment(id,request.reason().trim());
        mapper.update(delivery);
        mapper.event(delivery.getOrderId(),user.getId(),"VOID_SHIPMENT","更正错误发货登记 "+shipment.getShipmentNo()+"："+request.reason().trim()+"。原记录保留，此操作不代表实际退货。");
    }

    // 开始执行后，每次订单修改都需要当前负责人确认；订单版本变化会使上次确认自然失效。
    public void checkOrderChange(Long id,List<SalesOrderItem> proposed,boolean cancel) {
        var delivery=mapper.find(id);if(delivery==null) return;
        var lines=mapper.lines(id);
        boolean shipped=lines.stream().anyMatch(line->line.getShippedQuantity()>0);
        if(cancel && shipped) throw new BusinessException("订单已有实际发货，不能整单取消");
        if(!cancel && delivery.getStatus().equals("SHIPPED")) throw new BusinessException("订单已全部发货，产品调整请走后续售后流程");
        if(Boolean.TRUE.equals(delivery.getStarted())) {
            if(delivery.getProductionId()!=null && !Objects.equals(delivery.getProductionAck(),delivery.getOrderVersion())) throw new BusinessException("请先由生产负责人确认当前订单的变更/取消影响");
            if(delivery.getWarehouseId()!=null && !Objects.equals(delivery.getWarehouseAck(),delivery.getOrderVersion())) throw new BusinessException("请先由仓储负责人确认当前订单的变更/取消影响");
        }
        if(proposed==null) return;
        var old=orders.items(id);
        for(var line:lines) if(line.getShippedQuantity()>0) {
            var next=proposed.stream().filter(item->Objects.equals(item.getId(),line.getId())).findFirst().orElseThrow(()->new BusinessException("不能删除已发货的产品明细"));
            var original=old.stream().filter(item->item.getId().equals(line.getId())).findFirst().orElseThrow();
            if(next.getQuantity()<line.getShippedQuantity()) throw new BusinessException("订购数量不能少于已发数量");
            if(!next.getSkuId().equals(original.getSkuId()) || !Objects.equals(next.getRequirements(),original.getRequirements()) || next.getUnitPrice().compareTo(original.getUnitPrice())!=0) throw new BusinessException("已发货产品不能更换SKU、定制要求或成交单价");
        }
    }

    private DeliveryVO authorized(Long id,User user,String code,boolean lock) {
        var order=orders.find(id);if(order==null) throw new BusinessException("订单不存在");
        if(lock) {workflow.customer(order.getCustomerId(),true);workflow.lockOpportunity(order.getOpportunityId());quotes.lock(order.getQuotationId());orders.lock(id);}
        var delivery=mapper.find(id);if(delivery==null) throw new BusinessException("订单尚未确认，不在交付列表中");
        access.check(allowed(delivery,user,code));return delivery;
    }
    private boolean allowed(DeliveryVO delivery,User user,String code) {
        var grants=access.scopes(user);var scope=grants.get(code);if(scope==null) return false;
        if(Objects.equals(user.getId(),delivery.getManagerId()) || Objects.equals(user.getId(),delivery.getProductionId()) || Objects.equals(user.getId(),delivery.getWarehouseId())) return true;
        var customer=grants.get("customer:list");
        return customer!=null && scope.owns(delivery.getOwnerId(),delivery.getOwnerDeptId()) && customer.owns(delivery.getOwnerId(),delivery.getOwnerDeptId());
    }
    public DeliveryVO summary(Long id) {return mapper.find(id);}
    private String personName(Long id) {return id==null?"未指定":mapper.users().stream().filter(user->user.getId().equals(id)).map(User::getRealName).findFirst().orElse("原负责人");}
    private boolean manager(DeliveryVO delivery,User user) {return allowed(delivery,user,"delivery:assign");}
    private User candidate(Long id,String code) {
        var employee=mapper.users().stream().filter(u->u.getId().equals(id)).findFirst().orElseThrow(()->new BusinessException("请选择有效在职员工"));
        var grants=access.scopes(employee);
        if(!grants.containsKey(code) || !grants.containsKey("delivery:progress:list")) throw new BusinessException("所选人员缺少对应交付职责权限");
        return employee;
    }
    private void active(DeliveryVO delivery) {if(!"CONFIRMED".equals(delivery.getOrderStatus())) throw new BusinessException("仅已确认且未完成、未取消的订单允许此操作");}
    private void version(DeliveryVO delivery,Integer version) {if(!delivery.getVersion().equals(version)) throw new BusinessException("交付信息已更新，请刷新后重试");}
    private String clean(String value) {return value==null?"":value.trim();}
    private void actions(DeliveryVO delivery,User user,Map<String,DataScope> grants) {
        if(!"CONFIRMED".equals(delivery.getOrderStatus())) return;
        for(String action:List.of("assign","production","stock","complete")) if(allowed(delivery,user,"delivery:"+action)) {
            if(action.equals("production") && !Objects.equals(delivery.getProductionId(),user.getId()) && !manager(delivery,user)) continue;
            if(action.equals("stock") && !Objects.equals(delivery.getWarehouseId(),user.getId()) && !manager(delivery,user)) continue;
            delivery.getActions().add(action);
        }
        if(allowed(delivery,user,"shipment:add") && (Objects.equals(delivery.getWarehouseId(),user.getId()) || manager(delivery,user))) delivery.getActions().add("ship");
        if(allowed(delivery,user,"shipment:void")) delivery.getActions().add("voidShipment");
        if(Objects.equals(delivery.getProductionId(),user.getId()) && grants.containsKey("delivery:production")) delivery.getActions().add("ackProduction");
        if(Objects.equals(delivery.getWarehouseId(),user.getId()) && grants.containsKey("delivery:stock")) delivery.getActions().add("ackStock");
    }
}
