package com.recipeapp.recipeserver.common;

import com.recipeapp.recipeserver.common.dto.ApiErrorResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The one place that turns a thrown Java exception into an HTTP error response.
 *
 * "Advice" is Spring's word for cross-cutting behavior — logic that applies across
 * many controllers rather than living inside any one of them. @RestControllerAdvice
 * means: watch EVERY controller in this application, and when one of them throws an
 * exception matching a method below, use that method's return value as the response.
 *
 * WHY THIS MATTERS: without it, a service calling .orElseThrow() produces a 500
 * Internal Server Error with a Java stack trace in the body. That's wrong twice over
 * — 500 means "the server is broken", but asking for a recipe that doesn't exist is
 * the CLIENT's mistake (a 4xx), and the frontend has no way to tell those apart.
 *
 * The payoff is that controllers and services stay clean: they express intent
 * ("this doesn't exist") and never mention status codes. Translation happens once.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // A "logger" writes diagnostic lines to the server console/log file. Spring Boot
    // bundles SLF4J for this. Using a logger rather than System.out.println gives you
    // timestamps, severity levels, and the ability to filter output by class.
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 404 NOT FOUND — the client asked for something that isn't there.
     *
     * @ExceptionHandler declares WHICH exception type this method handles. Spring
     * matches by type, including subclasses, so this catches our NotFoundException
     * from anywhere in the app.
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException ex) {
        // Deliberately NOT logged at error level. A 404 is normal traffic — a user
        // followed a stale link. Logging it as an error trains you to ignore errors.
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    /**
     * 400 BAD REQUEST — the request body failed the @Valid checks on a DTO.
     *
     * MethodArgumentNotValidException is what Spring throws when a @Valid @RequestBody
     * fails validation. It carries a full report of every constraint that failed,
     * which we flatten into a simple {field: message} map for the frontend.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // LinkedHashMap, not HashMap: it preserves insertion order, so the errors come
        // back in the order the fields are declared. A plain HashMap would scramble
        // them arbitrarily and the form's error list would reorder itself at random.
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        // 1. getBindingResult() holds the full validation report.
        // 2. getFieldErrors() narrows it to per-field failures (as opposed to
        //    whole-object rules, which we don't use).
        // 3. For each one, record field name -> the message from the annotation,
        //    e.g. "name" -> "Recipe name is required".
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            // putIfAbsent, not put: a field can violate several constraints at once
            // (both @NotBlank and @Size). Showing the first is enough — a user fixes
            // one problem at a time, and a stack of messages under one input is noise.
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        "The submitted data was not valid",
                        fieldErrors));
    }

    /**
     * 400 BAD REQUEST — an argument was structurally fine but semantically wrong.
     * We use IllegalArgumentException for rules that validation annotations can't
     * express, such as "this list contains a recipe id that doesn't exist".
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    /**
     * 500 INTERNAL SERVER ERROR — the catch-all for anything we didn't anticipate.
     *
     * This one IS logged at error level with the full exception, because reaching it
     * means a genuine bug. Note the asymmetry that makes this safe: the STACK TRACE
     * goes to the server log where you can read it, while the client gets only a
     * generic sentence. Never echo exception internals to a browser — messages from
     * deep in the stack can leak table names, file paths, and library versions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception while serving a request", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Something went wrong on the server. Check the server log for details."));
    }
}
