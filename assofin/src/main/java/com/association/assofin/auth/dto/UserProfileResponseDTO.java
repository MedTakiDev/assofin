package com.association.assofin.auth.dto;

import com.association.assofin.role.Role;

public record UserProfileResponseDTO(
        String name,
        String lastname,
        String email,
        Role role
) {}
