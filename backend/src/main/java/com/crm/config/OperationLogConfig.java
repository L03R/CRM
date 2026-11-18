package com.crm.config;

import com.crm.entity.OperationLog;
import com.crm.entity.User;
import com.crm.service.OperationLogService;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class OperationLogConfig implements WebMvcConfigurer {
    private final OperationLogService service;
    private static final Map<String,String> ACTIONS=Map.ofEntries(
            Map.entry("create","新增"),Map.entry("update","修改"),Map.entry("delete","删除"),Map.entry("status","启停"),
            Map.entry("state","账号状态调整"),Map.entry("resetPassword","重置密码"),Map.entry("changePassword","修改本人密码"),
            Map.entry("assignRoles","分配角色"),Map.entry("assignPermissions","配置权限"),Map.entry("permissions","配置权限"),
            Map.entry("createCategory","新增分类"),Map.entry("updateCategory","修改分类"),Map.entry("deleteCategory","删除分类"),
            Map.entry("createSku","新增SKU"),Map.entry("updateSku","修改SKU"),Map.entry("skuStatus","启停SKU"),Map.entry("logout","退出登录"),
            Map.entry("assign","分配或转交"),Map.entry("follow","新增跟进"),Map.entry("returnToQueue","退回待分配"),Map.entry("close","关闭线索"),
            Map.entry("convert","转为客户"),Map.entry("requestPool","申请退回公海"),Map.entry("reviewPool","审核公海回收"),Map.entry("claim","领取公海客户"),
            Map.entry("generate","生成草稿"),Map.entry("confirm","确认"),Map.entry("change","变更"),Map.entry("cancel","取消"),Map.entry("cancelConfirmed","取消已确认订单"));

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception error) {
                if (error!=null || response.getStatus()>=400 || !java.util.Set.of("POST","PUT","DELETE").contains(request.getMethod()) || !(handler instanceof HandlerMethod method)) return;
                var authentication=SecurityContextHolder.getContext().getAuthentication();
                if (authentication==null || !(authentication.getPrincipal() instanceof User user)) return;
                String module=switch (method.getBeanType().getSimpleName()) {
                    case "UserController" -> "员工"; case "RoleController" -> "角色"; case "DeptController" -> "部门";
                    case "PermissionController" -> "权限"; case "ProductController" -> "产品";
                    case "ProductAttributeController" -> "分类属性";
                    case "LeadController" -> "线索"; case "CustomerController" -> "客户"; case "OpportunityController" -> "商机";
                    case "DashboardController" -> "个人日程";
                    case "ContactController" -> "联系人";
                    case "QuotationController" -> "报价";
                    case "SalesOrderController" -> "销售订单";
                    case "DeliveryController" -> "交付进度";
                    case "ShipmentController" -> "发货记录";
                    case "AfterSaleController" -> "售后记录";
                    default -> "账号";
                };
                OperationLog log=new OperationLog();
                log.setUserId(user.getId()); log.setEmployeeNo(user.getEmployeeNo()); log.setRealName(user.getRealName());
                log.setOperation(module+"："+ACTIONS.getOrDefault(method.getMethod().getName(),"维护"));
                log.setHttpMethod(request.getMethod()); log.setResource(request.getRequestURI());
                service.record(log);
            }
        }).addPathPatterns("/api/**");
    }
}
