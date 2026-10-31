package com.association.assofin.role.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RolePageRequestDTO(
        @Min(0)
        int page,

        @Min(20)
        @Max(100)
        int size
) {}
