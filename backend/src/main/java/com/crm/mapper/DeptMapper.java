package com.crm.mapper;

import com.crm.entity.Dept;
import com.crm.vo.DeptVO;
import java.util.List;

public interface DeptMapper {
    List<DeptVO> findAll();
    DeptVO findById(Long id);
    void insert(Dept dept);
    int update(Dept dept);
    int delete(Long id);
    int countChildren(Long id);
    int countUsers(Long id);
    int countAvailableLeader(Long id);
    // Organization trees are small; lock in ID order during structural changes.
    List<Long> lockDepartments();
}
