package com.crm.service;

import com.crm.common.BusinessException;
import com.crm.dto.DeptRequest;
import com.crm.entity.Dept;
import com.crm.mapper.DeptMapper;
import com.crm.vo.DeptVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DeptService {
    private final DeptMapper mapper;

    // Search keeps matching departments and their ancestors, so the tree stays connected.
    public List<DeptVO> tree(String keyword) {
        List<DeptVO> departments = mapper.findAll();
        Map<Long, DeptVO> byId = new LinkedHashMap<>();
        departments.forEach(dept -> byId.put(dept.getId(), dept));
        Set<Long> visible = new HashSet<>();
        String search = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        for (DeptVO dept : departments) {
            if (dept.getDeptName().toLowerCase(Locale.ROOT).contains(search)
                    || dept.getDeptCode().toLowerCase(Locale.ROOT).contains(search)) {
                DeptVO current = dept;
                while (current != null && visible.add(current.getId())) {
                    current = byId.get(current.getParentId());
                }
            }
        }
        List<DeptVO> roots = new ArrayList<>();
        for (DeptVO dept : departments) {
            if (!visible.contains(dept.getId())) continue;
            DeptVO parent = byId.get(dept.getParentId());
            if (parent == null) roots.add(dept);
            else parent.getChildren().add(dept);
        }
        return roots;
    }

    public DeptVO detail(Long id) {
        DeptVO dept = mapper.findById(id);
        if (dept == null) throw new BusinessException("部门不存在");
        return dept;
    }

    @Transactional
    public Long create(DeptRequest request) {
        mapper.lockDepartments();
        validate(null, request);
        Dept dept = toEntity(request);
        mapper.insert(dept);
        return dept.getId();
    }

    @Transactional
    public void update(Long id, DeptRequest request) {
        mapper.lockDepartments();
        detail(id);
        validate(id, request);
        Dept dept = toEntity(request);
        dept.setId(id);
        mapper.update(dept);
    }

    @Transactional
    public void delete(Long id) {
        mapper.lockDepartments();
        detail(id);
        if (mapper.countChildren(id) > 0) throw new BusinessException("请先删除或迁移下级部门");
        if (mapper.countUsers(id) > 0) throw new BusinessException("请先迁移部门员工（包含离职员工）");
        mapper.delete(id);
    }

    private void validate(Long id, DeptRequest request) {
        Long parentId = request.parentId();
        while (parentId != null) {
            if (parentId.equals(id)) throw new BusinessException("不能将部门移动到自身或下级部门");
            parentId = detail(parentId).getParentId();
        }
        if (request.leaderUserId() != null && mapper.countAvailableLeader(request.leaderUserId()) == 0) {
            throw new BusinessException("负责人必须是账号启用且未删除的在职员工");
        }
    }

    private Dept toEntity(DeptRequest request) {
        Dept dept = new Dept();
        dept.setParentId(request.parentId());
        dept.setDeptName(request.deptName().trim());
        dept.setDeptCode(request.deptCode().trim());
        dept.setLeaderUserId(request.leaderUserId());
        dept.setSortNo(request.sortNo());
        dept.setStatus(request.status());
        dept.setRemark(request.remark());
        return dept;
    }
}
