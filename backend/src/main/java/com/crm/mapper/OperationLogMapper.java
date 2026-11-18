package com.crm.mapper;

import com.crm.entity.OperationLog;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface OperationLogMapper {
    void insert(OperationLog log);
    List<OperationLog> page(@Param("keyword") String keyword,@Param("offset") int offset,@Param("size") int size);
    long count(String keyword);
}
