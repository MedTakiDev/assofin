package com.association.assofin.role.dto.response;

import com.association.assofin.role.entity.Role;
import lombok.Builder;

import java.util.List;

@Builder
public record RolePageResponseDTO(
        List<RoleResponseDTO> content,
        int page,
        int size,
        long totalElements
) {}
