package com.association.assofin.handler;


import jakarta.mail.MessagingException;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.association.assofin.handler.BusinessErrorCodes.*;
import static org.springframework.http.HttpStatus.*;

@Slf4j
@RestControllerAdvice
//GlobalExceptionHandler — the central exception interceptor
public class GlobalExceptionHandler {

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ExceptionResponse> handleException(LockedException exp) {
        return ResponseEntity.status(UNAUTHORIZED).body(
                ExceptionResponse.builder()
                        .businessCode(ACCOUNT_LOCKED.getCode())
                        .exceptionDec(ACCOUNT_LOCKED.getDescription())
                        .errorMessage(exp.getMessage())
                        .build()
        );
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ExceptionResponse> handleException(DisabledException exp) {
        return ResponseEntity.status(UNAUTHORIZED).body(
                ExceptionResponse.builder()
                        .businessCode(ACCOUNT_DISABLED.getCode())
                        .exceptionDec(ACCOUNT_DISABLED.getDescription())
                        .errorMessage(exp.getMessage())
                        .build()
        );
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ExceptionResponse> handleException(BadCredentialsException exp) {
        return ResponseEntity.status(UNAUTHORIZED).body(
                ExceptionResponse.builder()
                        .businessCode(BAD_CREDENTIALS.getCode())
                        .exceptionDec(BAD_CREDENTIALS.getDescription())
                        .errorMessage(BAD_CREDENTIALS.getDescription())
                        .build()
        );
    }

    @ExceptionHandler(MessagingException.class)
    public ResponseEntity<ExceptionResponse> handleException(MessagingException exp) {
        return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(
                ExceptionResponse.builder()
                        .businessCode(SERVER_ERROR.getCode())
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
                                .businessCode(BAD_REQUEST_BUS.getCode())
                                .validationErrors(errors)
                                .build()
                );
    }

    @ExceptionHandler(EntityExistsException.class)
    public ResponseEntity<ExceptionResponse> handleException(EntityExistsException exp) {
        //todo implement logging mechanism needed
        return ResponseEntity
                .status(CONFLICT)
                .body(
                        ExceptionResponse.builder()
                                .businessCode(ENTITY_ALREADY_EXISTS.getCode())
                                .exceptionDec(exp.getMessage())
                                .errorMessage(exp.getMessage())
                                .build()
                );
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ExceptionResponse> handleException(EntityNotFoundException exp) {
        //todo implement logging mechanism needed
        return ResponseEntity
                .status(NOT_FOUND)
                .body(
                        ExceptionResponse.builder()
                                .businessCode(ENTITY_NOT_FOUND.getCode())
                                .exceptionDec(exp.getMessage())
                                .errorMessage(exp.getMessage())
                                .build()
                );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ExceptionResponse> handleException(DataIntegrityViolationException exp) {
        return ResponseEntity
                .status(CONFLICT)
                .body(ExceptionResponse.builder()
                        .businessCode(DATA_CONFLICT.getCode())
                        .exceptionDec("Constraint violation")
                        .errorMessage("The resource already exists or violates a constraint")
                        .build());
    }

    @ExceptionHandler(AccessDeniedException.class)//missing permissions
    public void handleAccessDenied(AccessDeniedException exp) {
        throw exp;
    }

    @ExceptionHandler(AuthenticationException.class)//401 authentication failed
    public void handleAuthentication(AuthenticationException exp) {
        throw exp;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleUnexpected(Exception exp) {
        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled exception [errorId={}]", errorId, exp);
        //todo LOG-01: replace the local UUID with MDC.get("requestId").
        return ResponseEntity
                .status(INTERNAL_SERVER_ERROR)
                .body(
                        ExceptionResponse.builder()
                                .errorId(errorId)
                                .businessCode(SERVER_ERROR.getCode())
                                .exceptionDec("An unexpected error occurred")
                                .errorMessage("Internal server error")
                                .build()
                );
    }


}
