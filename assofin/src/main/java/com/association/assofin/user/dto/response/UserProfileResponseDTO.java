package com.association.assofin.user.dto.response;

import com.association.assofin.role.entity.Role;
import lombok.Builder;

@Builder
public record UserProfileResponseDTO(
        long id,
        String firstname,
        String lastname,
        String email,
        Role role
) {}
