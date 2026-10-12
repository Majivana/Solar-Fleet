package za.co.solar.fleet.controller;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import za.co.solar.fleet.config.RequestIdFilter;

import java.util.List;
import java.util.Map;

/**
 * Single error contract for the API: RFC 9457 problem details, never a stack trace or internal message, always
 * carrying the request id. Extends {@link ResponseEntityExceptionHandler} so framework errors (404, 405, 415,
 * type mismatches, ...) keep their correct status codes instead of falling into the 500 fallback.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleStatus(ResponseStatusException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(exception.getStatusCode(),
                exception.getReason() == null ? "The request could not be completed." : exception.getReason());
        detail.setTitle("Request could not be completed");
        return withRequestId(detail);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::fieldError).toList();
        String summary = errors.isEmpty() ? "Check required fields and enter valid values."
                : "Check required fields and enter valid values: " + String.join("; ",
                        errors.stream().map(e -> e.get("field") + " " + e.get("message")).toList());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, summary);
        detail.setTitle("Check the form");
        detail.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(withRequestId(detail));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "The request contains an invalid value or is not valid JSON.");
        detail.setTitle("Invalid request");
        return ResponseEntity.badRequest().body(withRequestId(detail));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Check required fields and enter valid values.");
        detail.setTitle("Check the form");
        return withRequestId(detail);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleConstraint(DataIntegrityViolationException exception) {
        log.warn("Data integrity violation: {}", exception.getMostSpecificCause().getClass().getSimpleName());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "This change conflicts with existing project or equipment data.");
        detail.setTitle("Conflicting record");
        return withRequestId(detail);
    }

    /** Last resort: log the real cause server-side, tell the client only that something went wrong. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unhandled error while processing request", exception);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on our side. Quote the request id if you contact support.");
        detail.setTitle("Unexpected error");
        return withRequestId(detail);
    }

    private static Map<String, String> fieldError(FieldError error) {
        return Map.of("field", error.getField(),
                "message", error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage());
    }

    private static ProblemDetail withRequestId(ProblemDetail detail) {
        String id = MDC.get(RequestIdFilter.MDC_KEY);
        if (id != null) detail.setProperty("requestId", id);
        return detail;
    }
}
