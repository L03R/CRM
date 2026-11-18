package com.crm.config;

import com.crm.entity.User;
import com.crm.mapper.AuthMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "crm.bootstrap.test-employees", havingValue = "true")
public class TestEmployeeInitializer implements ApplicationRunner {
    private final AuthMapper mapper;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        create("MANAGER001", "张经理", "DEPT_SALES", "Manager@123456", "SALES_MANAGER");
        create("SALES001", "李华东", "DEPT_SALES_EAST", "Sales@123456", "SALES_STAFF");
        create("SALES002", "陈华南", "DEPT_SALES_SOUTH", "Sales2@123456", "SALES_STAFF");
        create("MARKET001", "林市场", "DEPT_MARKETING", "Market@123456", "MARKETING");
        create("SERVICE001", "周客服", "DEPT_SERVICE", "Service@123456", "CUSTOMER_SERVICE");
        create("PROD001", "赵生产", "DEPT_PRODUCTION", "Produce@123456", "PRODUCTION");
        create("FIN001", "王财务", "DEPT_FINANCE", "Finance@123456", "FINANCE");
        create("MULTI001", "刘协作", "DEPT_SALES_EAST", "Multi@123456", "SALES_STAFF", "CUSTOMER_SERVICE");
        create("SUPPLY001", "吴采购", "DEPT_SUPPLY", "Supply@123456", "SUPPLY_STAFF");
        create("PRODUCT001", "郑产品", "DEPT_PRODUCTION", "Product@123456", "PRODUCT_MANAGER");
    }

    private void create(String employeeNo, String name, String deptCode, String password, String... roleCodes) {
        if (mapper.findByEmployeeNo(employeeNo) != null) return;
        User user = new User();
        user.setEmployeeNo(employeeNo);
        user.setRealName(name);
        user.setDeptId(jdbc.queryForObject("SELECT id FROM sys_dept WHERE dept_code=? AND deleted=0", Long.class, deptCode));
        user.setPasswordHash(encoder.encode(password));
        mapper.insertUser(user);
        for (String roleCode : roleCodes) {
            int inserted = jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=? AND status=1 AND deleted=0",
                    user.getId(), roleCode);
            if (inserted == 0) throw new IllegalStateException("缺少初始角色：" + roleCode);
        }
    }
}
