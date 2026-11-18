package com.crm.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record CreateEmployeeRequest(
        @NotBlank(message = "请输入员工编号") @Pattern(regexp = "[A-Za-z0-9_-]{3,32}", message = "员工编号为3-32位字母、数字、下划线或短横线") String employeeNo,
        @NotBlank(message = "请输入姓名") @Size(max = 64) String realName,
        @NotNull(message = "请选择所属部门") @Positive Long deptId,
        @Size(max = 20) String phone,
        @Email(message = "邮箱格式不正确") @Size(max = 128) String email,
        LocalDate hireDate,
        @Size(max = 500) String remark,
        @NotNull(message = "角色列表不能为空，可传空数组") List<@NotNull @Positive Long> roleIds
) {}
