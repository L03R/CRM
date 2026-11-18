package com.crm.service;

import com.crm.common.BusinessException;
import com.crm.dto.PermissionRequest;
import com.crm.entity.Permission;
import com.crm.mapper.AuthMapper;
import com.crm.mapper.PermissionMapper;
import com.crm.mapper.UserMapper;
import com.crm.vo.PermissionTreeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PermissionManageService {
    private final PermissionMapper mapper;
    private final UserMapper userMapper;
    private final AuthMapper authMapper;
    private static final Set<String> PROTECTED = Set.of("menu:system", "menu:system:permission",
            "system:permission:list", "system:permission:add", "system:permission:edit",
            "system:permission:status", "system:permission:delete");
    private static final Map<String, String> COMPONENTS = Map.ofEntries(
            Map.entry("customer/contact","联系人"),
            Map.entry("workbench/todo","待办事项"),Map.entry("workbench/schedule","我的日程"),
            Map.entry("analysis/lead","线索转化分析"),Map.entry("analysis/customer","客户与跟进分析"),Map.entry("analysis/business","销售商机分析"),
            Map.entry("workbench/index","工作概览"),Map.entry("clue/index","线索管理"),Map.entry("customer/index","客户档案"),Map.entry("business/index","商机管理"),Map.entry("order/index","销售订单"),
            Map.entry("customer/pool","客户公海"),Map.entry("customer/follow","跟进记录"),Map.entry("sales/quotation","报价管理"),
            Map.entry("delivery/progress","订单交付进度"),Map.entry("delivery/shipment","发货记录"),Map.entry("delivery/after-sale","售后记录"),
            Map.entry("finance/receipt","回款记录"),Map.entry("finance/invoice","开票记录"),
            Map.entry("system/employee","员工管理"),Map.entry("system/dept","部门管理"),Map.entry("system/role","角色管理"),Map.entry("system/permission","权限管理"),
            Map.entry("product/category","产品分类"),Map.entry("product/index","产品档案"),Map.entry("system/log","操作日志"));

    public boolean isAdmin(Long userId) {
        return authMapper.findRoles(userId).stream().anyMatch(r -> r.getRoleCode().equals("SUPER_ADMIN"));
    }

    public Map<String, String> components() { return COMPONENTS; }

    public List<PermissionTreeVO> tree(String keyword, Integer type) {
        Map<Long, PermissionTreeVO> nodes = new LinkedHashMap<>();
        for (Permission permission : mapper.findAll()) {
            PermissionTreeVO node = new PermissionTreeVO();
            BeanUtils.copyProperties(permission, node);
            node.setProtectedNode(PROTECTED.contains(permission.getPermissionCode()));
            nodes.put(node.getId(), node);
        }
        String search = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        Set<Long> visible = new HashSet<>();
        for (PermissionTreeVO node : nodes.values()) {
            if ((type == null || type.equals(node.getPermissionType())) &&
                    (node.getPermissionName().toLowerCase(Locale.ROOT).contains(search) || node.getPermissionCode().toLowerCase(Locale.ROOT).contains(search))) {
                PermissionTreeVO current = node;
                while (current != null && visible.add(current.getId())) current = nodes.get(current.getParentId());
            }
        }
        List<PermissionTreeVO> roots = new ArrayList<>();
        for (PermissionTreeVO node : nodes.values()) {
            if (!visible.contains(node.getId())) continue;
            if (node.getParentId() == null) roots.add(node);
            else nodes.get(node.getParentId()).getChildren().add(node);
        }
        return roots;
    }

    @Transactional
    public Long create(PermissionRequest request) {
        Long adminRoleId = userMapper.lockAdminRole();
        Permission permission = toEntity(request);
        validate(permission);
        mapper.insert(permission);
        mapper.grantAdmin(adminRoleId, permission.getId());
        return permission.getId();
    }

    @Transactional
    public void update(Long id, PermissionRequest request) {
        userMapper.lockAdminRole();
        Permission existing = editable(id);
        if (!existing.getPermissionCode().equals(request.permissionCode()) || !existing.getPermissionType().equals(request.permissionType())) {
            throw new BusinessException("权限编码和类型创建后不能修改");
        }
        Permission permission = toEntity(request);
        permission.setId(id);
        validate(permission);
        mapper.update(permission);
    }

    @Transactional
    public void status(Long id, Integer status) {
        userMapper.lockAdminRole();
        editable(id);
        mapper.updateStatus(id, status);
    }

    @Transactional
    public void delete(Long id) {
        userMapper.lockAdminRole();
        editable(id);
        if (mapper.countChildren(id) > 0) throw new BusinessException("请先删除或迁移下级权限");
        if (mapper.countBusinessGrants(id) > 0) throw new BusinessException("请先解除其他角色对此权限的授权");
        mapper.deleteAdminGrant(id);
        mapper.delete(id);
    }

    private Permission editable(Long id) {
        Permission permission = mapper.findById(id);
        if (permission == null) throw new BusinessException("权限不存在");
        if (PROTECTED.contains(permission.getPermissionCode())) throw new BusinessException("系统管理入口及其关键权限受保护，不能修改、停用或删除");
        return permission;
    }

    private void validate(Permission permission) {
        Integer type = permission.getPermissionType();
        Permission parent = permission.getParentId() == null ? null : mapper.findById(permission.getParentId());
        if (permission.getParentId() != null && parent == null) throw new BusinessException("上级权限不存在");
        if ((type == 3 && (parent == null || parent.getPermissionType() != 2)) ||
                (type != 3 && parent != null && parent.getPermissionType() != 1)) {
            throw new BusinessException("操作必须属于菜单，目录和菜单只能挂在目录下");
        }
        for (Permission current = parent; current != null; current = current.getParentId() == null ? null : mapper.findById(current.getParentId())) {
            if (current.getId().equals(permission.getId())) throw new BusinessException("不能移动到自身或下级节点");
        }
        boolean menuCode = permission.getPermissionCode().startsWith("menu:");
        if (type == 3 ? menuCode : !menuCode) throw new BusinessException("目录和菜单编码使用 menu: 前缀，操作编码不能使用该前缀");
        if (type == 2 && (permission.getComponent() == null || !COMPONENTS.containsKey(permission.getComponent()))) throw new BusinessException("请选择已经开发的页面组件");
        String path = permission.getRoutePath();
        if (type == 2 || (path != null && !path.isBlank())) {
            if (path == null || !path.matches("/(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_-]+") ||
                    path.equals("/login") || path.equals("/403") || path.equals("/api") || path.startsWith("/api/")) {
                throw new BusinessException("请填写有效页面路由，如 /system/example，不能使用登录或接口地址");
            }
            if (mapper.findAll().stream().anyMatch(p -> !p.getId().equals(permission.getId()) && path.equals(p.getRoutePath()))) {
                throw new BusinessException("页面路由已存在");
            }
        }
    }

    private Permission toEntity(PermissionRequest request) {
        Permission permission = new Permission();
        permission.setPermissionName(request.permissionName().trim());
        permission.setPermissionCode(request.permissionCode());
        permission.setPermissionType(request.permissionType());
        permission.setParentId(request.parentId());
        permission.setRoutePath(request.permissionType() == 3 ? null : request.routePath());
        permission.setComponent(request.permissionType() == 2 ? request.component() : null);
        permission.setIcon(request.permissionType() == 3 ? null : request.icon());
        permission.setSortNo(request.sortNo());
        permission.setVisible(request.permissionType() == 3 ? 0 : request.visible());
        permission.setStatus(request.status());
        return permission;
    }
}
