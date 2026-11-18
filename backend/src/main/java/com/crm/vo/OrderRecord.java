package com.crm.vo;

import com.crm.entity.SalesOrder;
import com.crm.entity.SalesOrderItem;
import java.util.List;

public record OrderRecord(SalesOrder order, List<SalesOrderItem> items) {}
