package com.crm.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class PageQuery {
    @Min(1) @Max(1000000) private int page=1;
    @Min(1) @Max(100) private int size=20;
    public int getOffset() {return (page-1)*size;}
}
