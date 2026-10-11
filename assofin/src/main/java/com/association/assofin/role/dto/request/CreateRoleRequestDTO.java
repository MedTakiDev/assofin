package com.association.assofin.role.dto.request;

import java.util.List;

public record CreateRoleRequestDTO(
        String name,
        List<String> permissions
) {
}
