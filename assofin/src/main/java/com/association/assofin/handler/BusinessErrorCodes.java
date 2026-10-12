package com.association.assofin.handler;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;
//BusinessErrorCodes — your business error catalog
public enum BusinessErrorCodes {

    NO_CODE(0, NOT_IMPLEMENTED, "No code "),
    INCORRECT_CURRENT_PASSWORD(300, BAD_REQUEST, "Incorrect password"),
    NEW_PASSWORD_DOES_NOT_MATCH(301, BAD_REQUEST, "The new password does not match"),
    ACCOUNT_LOCKED(302, FORBIDDEN, "User account is locked"),
    ACCOUNT_DISABLED(303, FORBIDDEN, "User account is disabled"),
    BAD_CREDENTIALS(304, FORBIDDEN, "Login and / or password is incorrect"),
    BAD_REQUEST_BUS(305,BAD_REQUEST, "Bad request"),
    ENTITY_ALREADY_EXISTS(409, CONFLICT, "Entity already exists"),
    ENTITY_NOT_FOUND(404, NOT_FOUND, "Entity does not exists"),
    DATA_CONFLICT(410, CONFLICT, "The request conflicts with existing data"),
    SERVER_ERROR(500,INTERNAL_SERVER_ERROR, "internal server error"),




    ;

    @Getter
    private final int code;
    @Getter
    private final HttpStatus httpStatus;
    @Getter
    private final String description;

    BusinessErrorCodes(int code, HttpStatus httpStatus, String description ) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.description = description;
    }
}
