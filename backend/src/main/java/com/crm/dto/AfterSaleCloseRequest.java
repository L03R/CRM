package com.crm.dto;
import jakarta.validation.constraints.*;
public record AfterSaleCloseRequest(@NotNull Integer version,
    @NotBlank @Pattern(regexp="GUIDE|REPAIR|REPLACE|RESEND|RETURN|OTHER") String resolutionType,
    @NotBlank @Size(max=2000) String resultNote,@NotBlank @Size(max=1000) String customerFeedback,@Size(max=128) String externalNo) {}
