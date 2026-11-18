package com.crm.dto;
import jakarta.validation.constraints.*;
public record ContactRequest(
        @NotNull Long customerId,@NotBlank @Size(max=64) String contactName,
        @Size(max=64) String jobTitle,@Size(max=32) String phone,@Size(max=32) String landline,
        @Size(max=64) String wechat,@Email @Size(max=128) String email,
        @NotNull @Min(0) @Max(1) Integer status,@Size(max=1000) String remark) {}
