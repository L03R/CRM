package com.crm.mapper;
import com.crm.dto.AfterSaleQuery;
import com.crm.entity.*;
import com.crm.vo.*;
import com.crm.security.DataScope;
import org.apache.ibatis.annotations.Param;
import java.util.*;
public interface AfterSaleMapper {
    AfterSaleVO find(Long id);
    AfterSaleVO lock(Long id);
    List<AfterSaleVO> page(@Param("scope") DataScope scope,@Param("customer") DataScope customer,@Param("query") AfterSaleQuery query);
    long count(@Param("scope") DataScope scope,@Param("customer") DataScope customer,@Param("query") AfterSaleQuery query);
    void insert(AfterSale record);
    void update(AfterSale record);
    List<AfterSaleItem> items(Long id);
    void deleteItems(Long id);
    void insertItem(AfterSaleItem item);
    void event(@Param("id") Long id,@Param("userId") Long userId,@Param("action") String action,@Param("content") String content,@Param("occurredAt") java.time.LocalDateTime occurredAt);
    List<AfterSaleEvent> events(Long id);
    List<AfterSaleOrderOption> orders(Long customerId);
    List<Contact> contacts(Long customerId);
    List<Map<String,Object>> owners(@Param("scope") DataScope scope,@Param("customer") DataScope customer);
    List<AfterSaleImage> images(Long id);
    AfterSaleImage image(@Param("caseId") Long caseId,@Param("id") Long id);
    void insertImage(AfterSaleImage image);
}
