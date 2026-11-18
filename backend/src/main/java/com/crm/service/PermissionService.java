package com.crm.service;

import com.crm.entity.Permission;
import com.crm.mapper.AuthMapper;
import com.crm.vo.MenuVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private final AuthMapper mapper;

    // Only explicitly granted permissions are authorities. Ancestors are added for menu display.
    public List<Permission> permissions(Long userId) {
        List<Permission> granted = mapper.findGrantedPermissions(userId);
        Map<Long, Permission> enabled = new LinkedHashMap<>();
        mapper.findEnabledPermissions().forEach(p -> enabled.put(p.getId(), p));
        Map<Long, Permission> result = new LinkedHashMap<>();
        for (Permission permission : granted) {
            List<Permission> path = new ArrayList<>();
            Permission current = permission;
            while (current != null) {
                path.add(current);
                if (current.getParentId() == null) break;
                current = enabled.get(current.getParentId());
            }
            // A disabled ancestor disables this branch too.
            if (current == null) continue;
            for (Permission p : path) {
                if (p.getId().equals(permission.getId()) || p.getPermissionType() != 3) {
                    result.put(p.getId(), p);
                }
            }
        }
        return result.values().stream()
                .sorted(Comparator.comparing(Permission::getSortNo).thenComparing(Permission::getId)).toList();
    }

    public List<String> codes(List<Permission> permissions) {
        return permissions.stream().filter(p -> p.getPermissionType() == 3)
                .map(Permission::getPermissionCode).toList();
    }

    public List<MenuVO> menus(Long userId) {
        Map<Long, MenuVO> nodes = new LinkedHashMap<>();
        for (Permission permission : permissions(userId)) {
            if (permission.getPermissionType() == 3 || permission.getVisible() == 0) continue;
            MenuVO menu = new MenuVO();
            BeanUtils.copyProperties(permission, menu);
            nodes.put(menu.getId(), menu);
        }
        List<MenuVO> roots = new ArrayList<>();
        for (MenuVO menu : nodes.values()) {
            if (menu.getParentId() == null) roots.add(menu);
            else if (nodes.containsKey(menu.getParentId())) nodes.get(menu.getParentId()).getChildren().add(menu);
        }
        removeEmptyDirectories(roots);
        return roots;
    }

    private void removeEmptyDirectories(List<MenuVO> menus) {
        for (MenuVO menu : menus) removeEmptyDirectories(menu.getChildren());
        menus.removeIf(menu -> menu.getPermissionType() == 1 && menu.getChildren().isEmpty());
    }
}
