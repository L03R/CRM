package com.crm.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper=true)
public class OrderQuery extends PageQuery {
    private String keyword;
    private String status;
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate dateFrom;
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate dateTo;
}
