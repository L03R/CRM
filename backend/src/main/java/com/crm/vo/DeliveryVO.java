package com.crm.vo;
import com.crm.entity.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.*;
import java.util.*;
@Data @EqualsAndHashCode(callSuper=true)
public class DeliveryVO extends OrderDelivery {
    private String orderNo,orderStatus,customerName,ownerName,managerName,productionName,warehouseName,status;
    private Long customerId,opportunityId,ownerId,ownerDeptId;
    private Integer orderVersion;
    private LocalDate promisedDate,requestedDate;
    private String recipientName,recipientPhone,shippingAddress;
    private boolean overdue;
    private List<DeliveryLineVO> items=List.of();
    private List<DeliveryEventVO> events=List.of();
    private List<Shipment> shipments=List.of();
    private List<String> actions=new ArrayList<>();
}
