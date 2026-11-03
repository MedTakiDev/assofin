package com.association.assofin.handler;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;

/**
 * BusinessErrorCodes: the business error catalog. Each entry is a stable code that clients can
 * branch on, the HTTP status it is meant for, and a short description.
 *
 * <p>The catalog is the single source of truth: GlobalExceptionHandler takes the HTTP status from
 * {@link #getHttpStatus()}, so an entry's status is the status the client receives.
 *
 * <p>To add an error: add a constant here, then use it from a handler in GlobalExceptionHandler.
 * Never reuse or renumber an existing code; clients may depend on it.
 *
 * <p>Naming: don't give a constant the same name as an {@link HttpStatus} constant (e.g.
 * METHOD_NOT_ALLOWED). Because of the static import, the name would refer to this enum's own
 * constant and fail to compile, and it becomes ambiguous in classes importing both.
 */
public enum BusinessErrorCodes {

    NO_CODE(0, NOT_IMPLEMENTED, "No code"),

    // Authentication and account (3xx range)
    INCORRECT_CURRENT_PASSWORD(300, BAD_REQUEST, "Incorrect password"),
    NEW_PASSWORD_DOES_NOT_MATCH(301, BAD_REQUEST, "The new password does not match"),
    // ACCOUNT_LOCKED / ACCOUNT_DISABLED are internal (logs, audit): the login response always uses
    // BAD_CREDENTIALS so it does not reveal whether an account exists or what state it is in.
    ACCOUNT_LOCKED(302, UNAUTHORIZED, "User account is locked"),
    ACCOUNT_DISABLED(303, UNAUTHORIZED, "User account is disabled"),
    BAD_CREDENTIALS(304, UNAUTHORIZED, "Login and / or password is incorrect"),
    AUTHENTICATION_REQUIRED(312, UNAUTHORIZED, "Authentication required"),
    ACCESS_DENIED(313, FORBIDDEN, "Access denied"),

    // Request errors detected by Spring MVC or Bean Validation
    BAD_REQUEST_BUS(305, BAD_REQUEST, "Bad request"),
    VALIDATION_FAILED(306, BAD_REQUEST, "Validation failed"),
    MALFORMED_REQUEST(307, BAD_REQUEST, "Malformed or unreadable request"),
    ENDPOINT_NOT_FOUND(308, NOT_FOUND, "Endpoint not found"),
    HTTP_METHOD_NOT_ALLOWED(309, METHOD_NOT_ALLOWED, "HTTP method not allowed"),
    MEDIA_TYPE_NOT_SUPPORTED(310, UNSUPPORTED_MEDIA_TYPE, "Media type not supported"),
    MEDIA_TYPE_NOT_ACCEPTABLE(311, NOT_ACCEPTABLE, "Requested media type not acceptable"),

    // Data
    ENTITY_ALREADY_EXISTS(409, CONFLICT, "Entity already exists"),
    ENTITY_NOT_FOUND(404, NOT_FOUND, "Entity does not exist"),
    DATA_CONFLICT(410, CONFLICT, "The request conflicts with existing data"),

    // Server and infrastructure
    SERVER_ERROR(500, INTERNAL_SERVER_ERROR, "Internal server error"),
    MAIL_SERVICE_UNAVAILABLE(503, SERVICE_UNAVAILABLE, "Email service unavailable"),
    ;

    @Getter
    private final int code;
    @Getter
    private final HttpStatus httpStatus;
    @Getter
    private final String description;

    BusinessErrorCodes(int code, HttpStatus httpStatus, String description) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.description = description;
    }
}
