package com.crm.dto;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true)
public class DeliveryQuery extends PageQuery {
    private String keyword,status;
    private Boolean overdue;
}
