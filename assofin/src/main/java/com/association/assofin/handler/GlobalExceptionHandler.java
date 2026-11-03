package com.association.assofin.handler;


import jakarta.mail.MessagingException;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.*;

import static com.association.assofin.handler.BusinessErrorCodes.*;

/**
 * GlobalExceptionHandler: the central exception interceptor for every controller.
 *
 * <p>How a handler is chosen: Spring picks the {@code @ExceptionHandler} whose declared type is the
 * <b>closest</b> to the thrown exception (subclass before superclass). The order of the methods in
 * this file does not matter.
 *
 * <p>The class has four kinds of handlers:
 * <ol>
 *   <li><b>Application / business exceptions</b>: our own {@code @ExceptionHandler} methods
 *       (login, entities, data conflicts, mail, bean validation). To handle a new exception type,
 *       add a method here and, if needed, a code in {@link BusinessErrorCodes}.</li>
 *   <li><b>Spring MVC framework exceptions</b> (unknown URL, wrong HTTP method, malformed JSON,
 *       request validation, ...): inherited from {@link ResponseEntityExceptionHandler}, which already
 *       knows the right status for each. We override {@link #handleExceptionInternal} to convert them
 *       to {@link ExceptionResponse}, and the validation hooks to add field errors.
 *       Never add an {@code @ExceptionHandler} for a type the parent already handles: startup fails
 *       with "Ambiguous @ExceptionHandler method". Override the parent's {@code handleXxx} instead.</li>
 *   <li><b>Spring Security exceptions</b>: rethrown so they reach ExceptionTranslationFilter, where
 *       RestAccessDeniedHandler (403) and RestAuthenticationEntryPoint (401) build the response.</li>
 *   <li><b>Catch-all</b> {@link #handleUnexpected}: 500, logged once with an errorId.</li>
 * </ol>
 *
 * <p>Rule for every handler: never return {@code exception.getMessage()} of a framework or library
 * exception to the client. It can contain SQL, class names or parser internals.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String DEFAULT_FIELD_MESSAGE = "invalid value";

    // =====================================================================================
    // 1. Application / business exceptions
    // =====================================================================================

    /**
     * Response for a catalog entry: status AND body come from {@link BusinessErrorCodes}.
     */
    private static ResponseEntity<ExceptionResponse> build(BusinessErrorCodes code) {
        return build(code, code.getDescription());
    }

    private static ResponseEntity<ExceptionResponse> build(BusinessErrorCodes code, @Nullable String message) {
        return ResponseEntity.status(code.getHttpStatus()).body(
                ExceptionResponse.builder()
                        .businessCode(code.getCode())
                        .exceptionDec(code.getDescription())
                        .errorMessage(Objects.requireNonNullElse(message, code.getDescription()))
                        .build()
        );
    }

    private static ExceptionResponse serverErrorBody(String errorId) {
        return ExceptionResponse.builder()
                .errorId(errorId)
                .businessCode(SERVER_ERROR.getCode())
                .exceptionDec("An unexpected error occurred")
                .errorMessage("Internal server error")
                .build();
    }

    private static ExceptionResponse validationBody(Map<String, String> fieldErrors, Set<String> globalErrors) {
        Set<String> messages = new LinkedHashSet<>(fieldErrors.values());
        messages.addAll(globalErrors);
        return ExceptionResponse.builder()
                .businessCode(VALIDATION_FAILED.getCode())
                .exceptionDec(VALIDATION_FAILED.getDescription())
                .validationErrors(messages)
                .errors(fieldErrors)
                .build();
    }

    /**
     * Several constraints on one field are joined with "; ". Map.merge with a null value would throw an NPE.
     */
    private static void addFieldError(Map<String, String> fieldErrors, String field, @Nullable String message) {
        fieldErrors.merge(field, Objects.requireNonNullElse(message, DEFAULT_FIELD_MESSAGE), (a, b) -> a + "; " + b);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ExceptionResponse> handleBadCredentials(BadCredentialsException exp) {
        return build(BAD_CREDENTIALS);
    }

    /**
     * Locked, disabled, expired account or expired credentials (all AccountStatusException).
     * Spring checks the account state BEFORE the password, so a specific response would tell anyone
     * who tries an email, with any password, that the account exists and is locked/disabled
     * (account enumeration, OWASP ASVS V2). The client gets the same body as a wrong password; the
     * real reason is only logged (and becomes an audit event with LOG-01).
     */
    @ExceptionHandler(AccountStatusException.class)
    public ResponseEntity<ExceptionResponse> handleAccountStatus(AccountStatusException exp) {
        log.debug("Login refused: account status {}", exp.getClass().getSimpleName());
        return build(BAD_CREDENTIALS);
    }

    // =====================================================================================
    // 2. Spring MVC framework exceptions (inherited from ResponseEntityExceptionHandler)
    // =====================================================================================

    @ExceptionHandler(EntityExistsException.class)
    public ResponseEntity<ExceptionResponse> handleEntityExists(EntityExistsException exp) {
        // The message is written by our own services (e.g. "Role with name ADMIN already exists").
        return build(ENTITY_ALREADY_EXISTS, exp.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ExceptionResponse> handleEntityNotFound(EntityNotFoundException exp) {
        // The message is written by our own services (e.g. "Role with id: 5 not found").
        // TODO(BUG-11/ERR-01): use a domain exception instead; Hibernate itself can throw this type
        //  with a message naming the entity class.
        return build(ENTITY_NOT_FOUND, exp.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ExceptionResponse> handleDataIntegrityViolation(DataIntegrityViolationException exp) {
        // The DB message names tables and constraints: never return it.
        log.debug("Data integrity violation ({})", exp.getMostSpecificCause().getClass().getSimpleName());
        return build(DATA_CONFLICT, "The resource already exists or violates a constraint");
    }

    // =====================================================================================
    // 3. Spring Security exceptions: rethrow so the security handlers own 401 / 403
    // =====================================================================================

    @ExceptionHandler(MessagingException.class)
    public ResponseEntity<ExceptionResponse> handleMessaging(MessagingException exp) {
        // The SMTP message can contain the host, the account and the server reply: log it, never return it.
        String errorId = UUID.randomUUID().toString();
        log.error("Mail delivery failed [errorId={}]", errorId, exp);
        return ResponseEntity.status(MAIL_SERVICE_UNAVAILABLE.getHttpStatus()).body(
                ExceptionResponse.builder()
                        .errorId(errorId)
                        .businessCode(MAIL_SERVICE_UNAVAILABLE.getCode())
                        .exceptionDec(MAIL_SERVICE_UNAVAILABLE.getDescription())
                        .errorMessage("The email could not be sent. Please try again later.")
                        .build()
        );
    }

    /**
     * Bean Validation on a {@code @Validated} class or service (AOP method validation).
     * ResponseEntityExceptionHandler does not cover it, so it needs its own handler.
     * This is jakarta.validation.ConstraintViolationException, NOT the Hibernate one:
     * database constraints arrive as DataIntegrityViolationException (handled above).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ExceptionResponse> handleConstraintViolation(ConstraintViolationException exp) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exp.getConstraintViolations().forEach(violation -> {
            // property path "createUser.request.email" -> field "email"
            String field = null;
            for (Path.Node node : violation.getPropertyPath()) {
                field = node.getName();
            }
            addFieldError(fieldErrors, Objects.requireNonNullElse(field, "value"), violation.getMessage());
        });
        return ResponseEntity.status(VALIDATION_FAILED.getHttpStatus()).body(validationBody(fieldErrors, Set.of()));
    }

    // =====================================================================================
    // 4. Catch-all: anything not handled above
    // =====================================================================================

    /**
     * {@code @Valid @RequestBody} failures. Replaces the former @ExceptionHandler method.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> addFieldError(fieldErrors, error.getField(), error.getDefaultMessage()));

        // Class-level constraints (e.g. "passwords must match") have no field.
        Set<String> globalErrors = new LinkedHashSet<>();
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> globalErrors.add(
                        Objects.requireNonNullElse(error.getDefaultMessage(), DEFAULT_FIELD_MESSAGE)));

        // The rejected value is deliberately never included: for a password field it IS the password.
        return handleExceptionInternal(ex, validationBody(fieldErrors, globalErrors), headers, status, request);
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    /**
     * Constraint failures on {@code @RequestParam} / {@code @PathVariable} (built-in method validation).
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = Objects.requireNonNullElse(result.getMethodParameter().getParameterName(), "parameter");
            result.getResolvableErrors()
                    .forEach(error -> addFieldError(fieldErrors, name, error.getDefaultMessage()));
        });

        // `status` is 400 for invalid parameters and 500 for an invalid return value: don't hard-code it.
        return handleExceptionInternal(ex, validationBody(fieldErrors, Set.of()), headers, status, request);
    }

    /**
     * Single funnel for every framework exception handled by ResponseEntityExceptionHandler.
     * Converts Spring's default ProblemDetail body into our ExceptionResponse format.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        // Same guard as the parent: once the response is committed nothing more can be written.
        if (request instanceof ServletWebRequest servletRequest
                && servletRequest.getResponse() != null
                && servletRequest.getResponse().isCommitted()) {
            return null;
        }

        ExceptionResponse response;
        if (statusCode.is5xxServerError()) {
            // e.g. HttpMessageNotWritableException, MissingPathVariableException: our bug, not the client's.
            String errorId = UUID.randomUUID().toString();
            log.error("Framework error [errorId={}]", errorId, ex);
            response = serverErrorBody(errorId);
        } else if (body instanceof ExceptionResponse alreadyBuilt) {
            // Built by the validation overrides above.
            response = alreadyBuilt;
        } else {
            // Client mistake: no stack trace, and never ex.getMessage() (it can contain parser internals).
            log.debug("Client error {} ({})", statusCode.value(), ex.getClass().getSimpleName());
            BusinessErrorCodes code = switch (statusCode.value()) {
                case 404 -> ENDPOINT_NOT_FOUND;
                case 405 -> HTTP_METHOD_NOT_ALLOWED;
                case 406 -> MEDIA_TYPE_NOT_ACCEPTABLE;
                case 415 -> MEDIA_TYPE_NOT_SUPPORTED;
                default -> MALFORMED_REQUEST;
            };
            // ProblemDetail.detail is Spring's client-safe text, e.g. "Method 'PUT' is not supported."
            String detail = (body instanceof ProblemDetail problemDetail) ? problemDetail.getDetail() : null;
            response = ExceptionResponse.builder()
                    .businessCode(code.getCode())
                    .exceptionDec(code.getDescription())
                    .errorMessage(Objects.requireNonNullElse(detail, code.getDescription()))
                    .build();
        }
        // Keep `headers`: they carry e.g. the Allow header of a 405.
        return new ResponseEntity<>(response, headers, statusCode);
    }

    /**
     * Missing permission (e.g. @PreAuthorize). Rethrow the SAME instance; a new one makes Spring log a WARN.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException exp) {
        throw exp;
    }

    /**
     * Authentication failures other than the login ones handled above (more specific types win: BadCredentials, AccountStatus).
     */
    @ExceptionHandler(AuthenticationException.class)
    public void handleAuthentication(AuthenticationException exp) {
        throw exp;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleUnexpected(Exception exp) {
        // TODO(LOG-01): replace the local UUID with MDC.get("requestId").
        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled exception [errorId={}]", errorId, exp);
        return ResponseEntity.status(SERVER_ERROR.getHttpStatus()).body(serverErrorBody(errorId));
    }
}
