package com.association.assofin.role.dto;

import java.util.List;

public record NewRoleRequestDto(
        String name,
        List<String> permissions
) {
}
