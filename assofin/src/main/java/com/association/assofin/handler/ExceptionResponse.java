package com.association.assofin.handler;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.Map;
import java.util.Set;

/**
 * ExceptionResponse: the single error body returned by the API (GlobalExceptionHandler,
 * RestAccessDeniedHandler, RestAuthenticationEntryPoint). Empty fields are omitted from the JSON.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExceptionResponse {

    /** Correlation id of a server-side failure (5xx only). The same value is written to the server log. */
    private String errorId;

    /** Stable code from {@link BusinessErrorCodes}; clients branch on this, never on the text. */
    private Integer businessCode;

    /** Short, fixed title of the error (the catalog description). */
    private String exceptionDec;

    /** Client-safe, human-readable explanation. Never a raw framework or library exception message. */
    private String errorMessage;

    /** All validation messages, without field names (kept for backward compatibility). */
    private Set<String> validationErrors;

    /** Validation errors per field or parameter: field name -> message(s), joined with "; ". */
    private Map<String, String> errors;
}
