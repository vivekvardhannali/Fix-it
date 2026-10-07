package com.fixit.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fixit.embedding.EmbeddingException;
import com.fixit.search.SearchNotConfiguredException;

/**
 * The single place that turns failures into the API's one error format ({@link ApiError}).
 * Rule: a client only ever sees a short, safe message - never exception names, SQL, class names or stack traces
 * (those go to the log). Extending ResponseEntityExceptionHandler covers Spring MVC's own errors (405, 415, 404 for
 * unknown paths, ...) with the correct status; anything unexpected falls through to a generic 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- our own domain errors ----------------------------------------------------------------

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Object> notFound(NotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<Object> forbidden(ForbiddenException e) {
        return respond(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<Object> badRequest(BadRequestException e) {
        return respond(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Object> alreadyExists(ConflictException e) {
        return respond(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<Object> invalidCredentials(InvalidCredentialsException e) {
        return respond(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<Object> tooManyRequests(TooManyRequestsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(ApiError.of(HttpStatus.TOO_MANY_REQUESTS, e.getMessage()));
    }

    /** Defensive: authorization failures raised inside controllers. (Filter-level ones are handled in SecurityConfig.) */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> accessDenied(AccessDeniedException e) {
        return respond(HttpStatus.FORBIDDEN, "Access denied");
    }

    /** E.g. two identical requests racing on a primary key. The client can simply retry. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> conflict(DataIntegrityViolationException e) {
        log.warn("Data integrity conflict: {}", e.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT, "Conflicting update, please retry");
    }

    /** Provider details stay in the logs; clients only learn the feature is temporarily unavailable. */
    @ExceptionHandler(EmbeddingException.class)
    ResponseEntity<Object> embeddingUnavailable(EmbeddingException e) {
        log.warn("Embedding failure: {}", e.getMessage(), e);
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "Search is temporarily unavailable");
    }

    @ExceptionHandler(SearchNotConfiguredException.class)
    ResponseEntity<Object> searchNotConfigured(SearchNotConfiguredException e) {
        log.warn("Search not configured: {}", e.getMessage());
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "Search is temporarily unavailable");
    }

    /** Last resort: log everything, tell the client nothing internal. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception e) {
        log.error("Unexpected error", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
    }

    // ---- Spring MVC's own errors (overrides give friendlier, safe messages) ----------------------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .sorted()
                .reduce((a, b) -> a + "; " + b)
                .orElse("Invalid request");
        return respond(HttpStatus.BAD_REQUEST, message);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Missing required parameter '" + e.getParameterName() + "'");
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(org.springframework.beans.TypeMismatchException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String name = e instanceof MethodArgumentTypeMismatchException m ? m.getName() : e.getPropertyName();
        return respond(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + name + "'");
    }

    /** Everything else Spring raises (405, 415, 404 unknown path, ...): right status, generic message. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String message = switch (status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status) {
            case NOT_FOUND -> "Resource not found";
            case METHOD_NOT_ALLOWED -> "Method not allowed";
            case UNSUPPORTED_MEDIA_TYPE -> "Unsupported content type";
            case NOT_ACCEPTABLE -> "Not acceptable";
            case BAD_REQUEST -> "Invalid request";
            default -> status != null && status.is4xxClientError() ? "Request could not be processed" : "Something went wrong";
        };
        return ResponseEntity.status(statusCode).headers(headers)
                .body(ApiError.of(status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status, message));
    }

    private static ResponseEntity<Object> respond(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiError.of(status, message));
    }
}
