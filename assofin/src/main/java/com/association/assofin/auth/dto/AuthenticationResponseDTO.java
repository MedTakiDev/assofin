package com.association.assofin.auth.dto;

import lombok.Builder;

@Builder
public record AuthenticationResponseDTO(
        String token,
        String message
) {}
