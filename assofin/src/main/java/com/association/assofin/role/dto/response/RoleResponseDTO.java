package com.association.assofin.role.dto.response;

import lombok.Builder;

import java.util.Set;

@Builder
public record RoleResponseDTO(
        Long id,
        String name,
        Set<String> permissions
) {}
