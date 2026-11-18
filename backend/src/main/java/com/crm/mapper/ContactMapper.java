package com.crm.mapper;
import com.crm.entity.Contact;
import com.crm.security.DataScope;
import com.crm.vo.*;
import org.apache.ibatis.annotations.Param;
import java.util.List;
public interface ContactMapper {
    List<ContactVO> page(@Param("scope") DataScope scope,@Param("readScope") DataScope readScope,@Param("customerId") Long customerId,@Param("pool") boolean pool,@Param("keyword") String keyword,@Param("status") Integer status,@Param("offset") int offset,@Param("size") int size);
    long count(@Param("scope") DataScope scope,@Param("readScope") DataScope readScope,@Param("customerId") Long customerId,@Param("pool") boolean pool,@Param("keyword") String keyword,@Param("status") Integer status);
    List<ContactCustomerVO> customers(@Param("scope") DataScope scope,@Param("readScope") DataScope readScope,@Param("keyword") String keyword);
    ContactVO find(Long id);
    ContactVO lock(Long id);
    Contact primary(Long customerId);
    void insert(Contact contact);
    void update(Contact contact);
    void clearPrimary(Long customerId);
    void setPrimary(Long id);
    Long firstActive(Long customerId);
}
