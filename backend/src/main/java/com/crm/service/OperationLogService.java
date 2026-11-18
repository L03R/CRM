package com.crm.service;

import com.crm.common.PageResult;
import com.crm.dto.PageQuery;
import com.crm.entity.OperationLog;
import com.crm.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationLogService {
    private final OperationLogMapper mapper;
    public void record(OperationLog log) {mapper.insert(log);}
    public PageResult<OperationLog> page(String keyword,PageQuery page) {
        return new PageResult<>(mapper.page(keyword,page.getOffset(),page.getSize()),mapper.count(keyword));
    }
}
