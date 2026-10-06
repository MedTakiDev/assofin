package com.association.assofin.handler;


import jakarta.mail.MessagingException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.naming.AuthenticationException;
import java.nio.file.AccessDeniedException;
import java.util.HashSet;
import java.util.Set;

import static com.association.assofin.handler.BusinessErrorCodes.*;
import static org.springframework.http.HttpStatus.*;

@RestControllerAdvice
//GlobalExceptionHandler — the central exception interceptor
public class GlobalExceptionHandler {

    // 🔒 1. Spring Security 6+ Method-level Access Denied (@PreAuthorize failure)
    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<ExceptionResponse> handleAccessDeniedException(Exception exp) {
        return ResponseEntity.status(FORBIDDEN).body(
                ExceptionResponse.builder()
                        .status(FORBIDDEN.value())
                        .exceptionDec("Access Denied")
                        .errorMessage("You do not have permission to access this resource.")
                        .build()
        );
    }

    // 🔒 2. Unauthenticated / Missing Credentials Exception
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ExceptionResponse> handleAuthenticationException(AuthenticationException exp) {
        return ResponseEntity.status(UNAUTHORIZED).body(
                ExceptionResponse.builder()
                        .status(UNAUTHORIZED.value())
                        .exceptionDec("Authentication Required")
                        .errorMessage(exp.getMessage())
                        .build()
        );
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ExceptionResponse> handleException(LockedException exp) {
        return ResponseEntity.status(UNAUTHORIZED).body(
                ExceptionResponse.builder()
                        .status(ACCOUNT_LOCKED.getCode())
                        .exceptionDec(ACCOUNT_LOCKED.getDescription())
                        .errorMessage(exp.getMessage())
                        .build()
        );
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ExceptionResponse> handleException(DisabledException exp) {
        return ResponseEntity.status(UNAUTHORIZED).body(
                ExceptionResponse.builder()
                        .status(ACCOUNT_DISABLED.getCode())
                        .exceptionDec(ACCOUNT_DISABLED.getDescription())
                        .errorMessage(exp.getMessage())
                        .build()
        );
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ExceptionResponse> handleException(BadCredentialsException exp) {
        return ResponseEntity.status(UNAUTHORIZED).body(
                ExceptionResponse.builder()
                        .status(BAD_CREDENTIALS.getCode())
                        .exceptionDec(BAD_CREDENTIALS.getDescription())
                        .errorMessage(BAD_CREDENTIALS.getDescription())
                        .build()
        );
    }

    @ExceptionHandler(MessagingException.class)
    public ResponseEntity<ExceptionResponse> handleException(MessagingException exp) {
        return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(
                ExceptionResponse.builder()
                        .errorMessage(exp.getMessage())
                        .build()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ExceptionResponse> handleException(MethodArgumentNotValidException exp) {
        Set<String> errors = new HashSet<>();
        exp.getBindingResult().getAllErrors().forEach((error) -> {
            var errorMsg = error.getDefaultMessage();
            errors.add(errorMsg);
        });

        return ResponseEntity
                .status(BAD_REQUEST)
                .body(
                    ExceptionResponse.builder()
                            .validationErrors(errors)
                        .build()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleException(Exception exp) {
        //todo implement logging mechanism needed
        exp.printStackTrace();
        return ResponseEntity
                .status(INTERNAL_SERVER_ERROR)
                .body(
                        ExceptionResponse.builder()
                                .exceptionDec("Internal Server Error")
                                .errorMessage(exp.getMessage())
                                .build()
                );
    }

}
