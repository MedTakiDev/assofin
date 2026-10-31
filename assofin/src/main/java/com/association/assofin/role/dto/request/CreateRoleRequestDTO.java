package com.association.assofin.role.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record CreateRoleRequestDTO(
        @NotNull
        String name,

        @NotNull
        Set<String> permissions
) {
}
