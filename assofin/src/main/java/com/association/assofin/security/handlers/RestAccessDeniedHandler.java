package com.association.assofin.security.handlers;


import com.association.assofin.handler.BusinessErrorCodes;
import com.association.assofin.handler.ExceptionResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        ExceptionResponse errorResponse = ExceptionResponse.builder()
                        .businessCode(BusinessErrorCodes.ACCESS_DENIED.getCode())
                        .exceptionDec(BusinessErrorCodes.ACCESS_DENIED.getDescription())
                        .errorMessage("You do not have permission to access this resource.")
                        .build();

        response.setStatus(BusinessErrorCodes.ACCESS_DENIED.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse)
        );
    }
}
