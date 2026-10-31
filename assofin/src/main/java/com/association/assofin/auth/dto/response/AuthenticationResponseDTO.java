package com.association.assofin.auth.dto.response;

import lombok.Builder;

@Builder
public record AuthenticationResponseDTO(
        String token,
        String message
) {}
