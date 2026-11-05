package com.association.assofin.security.handlers;

import com.association.assofin.handler.BusinessErrorCodes;
import com.association.assofin.handler.ExceptionResponse;
import com.association.assofin.security.cookie.AuthCookieService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;
    private final AuthCookieService authCookieService;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        // Clear the JWT cookie: it is missing, expired or invalid, so the browser should stop sending it.
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieService.createClearingCookie().toString());

        ExceptionResponse errorResponse = ExceptionResponse.builder()
                        .businessCode(BusinessErrorCodes.AUTHENTICATION_REQUIRED.getCode())
                        .exceptionDec(BusinessErrorCodes.AUTHENTICATION_REQUIRED.getDescription())
                        .errorMessage("Authentication is required to access this resource.")
                        .build();

        response.setStatus(BusinessErrorCodes.AUTHENTICATION_REQUIRED.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

}
