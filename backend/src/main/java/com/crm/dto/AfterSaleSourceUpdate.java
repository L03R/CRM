package com.crm.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public record AfterSaleSourceUpdate(@NotNull Integer version,@NotNull @Valid AfterSaleSourceRequest source,
                                    @NotBlank @Size(max=500) String note) {}
