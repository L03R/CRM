package com.crm.service;

import com.crm.common.BusinessException;
import com.crm.dto.RoleRequest;
import com.crm.entity.Permission;
import com.crm.entity.Role;
import com.crm.mapper.RoleMapper;
import com.crm.mapper.SystemQueryMapper;
import com.crm.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleMapper mapper;
    private final SystemQueryMapper queries;
    private final UserMapper userMapper;

    @Transactional
    public Long create(RoleRequest request) {
        Role role = toEntity(request);
        mapper.insert(role);
        return role.getId();
    }

    @Transactional
    public void update(Long id, RoleRequest request) {
        Role existing = editableRole(id);
        if (!existing.getRoleCode().equals(request.roleCode())) throw new BusinessException("角色编码创建后不能修改");
        Role role = toEntity(request);
        role.setId(id);
        mapper.update(role);
    }

    @Transactional
    public void delete(Long id) {
        editableRole(id);
        if (mapper.countEmployees(id) > 0) throw new BusinessException("请先移除该角色关联的员工（包含离职员工）");
        mapper.deletePermissions(id);
        mapper.delete(id);
    }

    @Transactional
    public void assignPermissions(Long id, List<Long> ids) {
        editableRole(id);
        Map<Long, Permission> byId = new HashMap<>();
        Map<String, Permission> byCode = new HashMap<>();
        queries.findPermissions().forEach(p -> { byId.put(p.getId(), p); byCode.put(p.getPermissionCode(), p); });
        Set<Long> selected = new LinkedHashSet<>();
        for (Long permissionId : ids) {
            Permission permission = byId.get(permissionId);
            addWithParents(permission, byId, selected);
            if (permission.getPermissionType() == 3) {
                String code = permission.getPermissionCode();
                  if(code.startsWith("contact:")) addWithParents(byCode.get("customer:list"),byId,selected);
                  if(code.startsWith("aftersale:")) {
                      addWithParents(byCode.get("aftersale:list"),byId,selected);
                      if(code.equals("aftersale:add")) addWithParents(byCode.get("customer:list"),byId,selected);
                  }
                  if(code.startsWith("delivery:") || code.startsWith("shipment:")) {
                      addWithParents(byCode.get("delivery:progress:list"),byId,selected);
                      if(code.startsWith("shipment:")) addWithParents(byCode.get("shipment:list"),byId,selected);
                      if(Set.of("delivery:assign","delivery:complete").contains(code)) addWithParents(byCode.get("customer:list"),byId,selected);
                  }
                  if(code.startsWith("order:")) {
                      addWithParents(byCode.get("customer:list"),byId,selected);
                        addWithParents(byCode.get("order:list"),byId,selected);
                        if(Set.of("order:add","order:confirm","order:cancel:confirmed").contains(code))addWithParents(byCode.get("business:list"),byId,selected);
                      if(code.equals("order:add"))addWithParents(byCode.get("quotation:list"),byId,selected);
                        if(Set.of("order:edit","order:change").contains(code))addWithParents(byCode.get("product:list"),byId,selected);
                  }
                  if(code.startsWith("quotation:")) {
                      addWithParents(byCode.get("customer:list"),byId,selected);
                      addWithParents(byCode.get("business:list"),byId,selected);
                      if(Set.of("quotation:add","quotation:edit","quotation:send","quotation:revise").contains(code)) addWithParents(byCode.get("product:list"),byId,selected);
                  }
                  if(code.startsWith("report:")) {
                      String source=Map.of("report:lead:list","clue:list","report:customer:list","customer:list","report:business:list","business:list").get(code);
                      if(source!=null) addWithParents(byCode.get(source),byId,selected);
                  }
                Permission view = byCode.get(code.substring(0, code.lastIndexOf(':')) + ":list");
                if (view != null) addWithParents(view, byId, selected);
                if (Set.of("clue:convert","customer:pool:claim","customer:pool:approve","customer:pool:request","customer:follow:add").contains(code))
                    addWithParents(byCode.get("customer:list"),byId,selected);
            }
        }
        mapper.deletePermissions(id);
        if (!selected.isEmpty()) mapper.insertPermissions(id, List.copyOf(selected));
    }

    private void addWithParents(Permission permission, Map<Long, Permission> byId, Set<Long> selected) {
        if (permission == null || permission.getStatus() != 1) throw new BusinessException("请选择有效且启用的权限，父级目录也必须启用");
        selected.add(permission.getId());
        if (permission.getParentId() != null) addWithParents(byId.get(permission.getParentId()), byId, selected);
    }

    private Role editableRole(Long id) {
        // Share the lock used by employee-role assignment, so role deletion cannot race with assignment.
        userMapper.lockAdminRole();
        Role role = mapper.lockRole(id);
        if (role == null) throw new BusinessException("角色不存在");
        if (role.getIsBuiltin() == 1 || role.getRoleCode().equals("SUPER_ADMIN")) {
            throw new BusinessException("内置角色不能修改、删除或调整授权");
        }
        return role;
    }

    private Role toEntity(RoleRequest request) {
        Role role = new Role();
        role.setRoleName(request.roleName().trim());
        role.setRoleCode(request.roleCode());
        role.setDataScope(request.dataScope());
        role.setStatus(request.status());
        role.setRemark(request.remark());
        return role;
    }
}
