package com.crm.dto;

import jakarta.validation.constraints.*;

public record DeptRequest(
        @Positive(message = "父部门ID必须大于0") Long parentId,
        @NotBlank(message = "部门名称不能为空") @Size(max = 64, message = "部门名称最多64个字符") String deptName,
        @NotBlank(message = "部门编码不能为空") @Size(max = 64, message = "部门编码最多64个字符") String deptCode,
        @Positive(message = "负责人ID必须大于0") Long leaderUserId,
        @NotNull(message = "排序不能为空") Integer sortNo,
        @NotNull(message = "状态不能为空") @Min(0) @Max(1) Integer status,
        @Size(max = 500, message = "备注最多500个字符") String remark
) {}
