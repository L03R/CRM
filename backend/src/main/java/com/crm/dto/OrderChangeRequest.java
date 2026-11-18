package com.crm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record OrderChangeRequest(@NotNull @Valid OrderDraftRequest content,
                                @NotBlank @Size(max=500) String reason,
                                @NotBlank @Size(max=500) String customerConfirmation) {}
