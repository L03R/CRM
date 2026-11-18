package com.crm.vo;
import com.crm.entity.*;
import java.util.List;
public record OrderSnapshot(Quotation quotation,List<QuotationItem> items) {}
