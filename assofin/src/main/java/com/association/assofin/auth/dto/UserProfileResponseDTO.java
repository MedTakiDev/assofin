package com.association.assofin.auth.dto;

import com.association.assofin.role.Role;
import lombok.Builder;

@Builder
public record UserProfileResponseDTO(
        long id,
        String firstname,
        String lastname,
        String email,
        Role role
) {}
