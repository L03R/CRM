package com.crm.mapper;
import com.crm.entity.*;
import com.crm.dto.DeliveryQuery;
import com.crm.security.DataScope;
import com.crm.vo.*;
import org.apache.ibatis.annotations.Param;
import java.util.*;
public interface DeliveryMapper {
    void ensure(Long orderId);
    DeliveryVO find(Long orderId);
    List<DeliveryVO> page(@Param("scope") DataScope scope,@Param("customer") DataScope customer,@Param("query") DeliveryQuery query);
    long count(@Param("scope") DataScope scope,@Param("customer") DataScope customer,@Param("query") DeliveryQuery query);
    List<DeliveryLineVO> lines(Long orderId);
    void update(OrderDelivery delivery);
    void event(@Param("orderId") Long orderId,@Param("actorId") Long actorId,@Param("action") String action,@Param("note") String note);
    List<DeliveryEventVO> events(Long orderId);
    List<User> users();
    Shipment shipment(Long id);
    Shipment byRequest(@Param("orderId") Long orderId,@Param("requestKey") String requestKey);
    List<Shipment> shipments(Long orderId);
    List<ShipmentItem> shipmentItems(Long id);
    void insertShipment(Shipment shipment);
    void insertShipmentItem(ShipmentItem item);
    void voidShipment(@Param("id") Long id,@Param("reason") String reason);
    List<Shipment> shipmentPage(@Param("scope") DataScope scope,@Param("customer") DataScope customer,@Param("query") DeliveryQuery query);
    long shipmentCount(@Param("scope") DataScope scope,@Param("customer") DataScope customer,@Param("query") DeliveryQuery query);
}
