package com.association.assofin.security.handlers;

import com.association.assofin.handler.BusinessErrorCodes;
import com.association.assofin.handler.ExceptionResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        /*
         * Clear the JWT cookie.
         *
         * The attributes used to delete the cookie should match
         * the attributes used when the cookie was created.
         */
        ResponseCookie cookie = ResponseCookie.from("token", "")
                .httpOnly(true)
                .secure(false) // true in production
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE,cookie.toString());

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
