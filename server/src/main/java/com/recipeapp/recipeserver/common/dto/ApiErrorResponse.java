package com.recipeapp.recipeserver.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * The shape of EVERY error this API returns. One consistent envelope, so the
 * frontend can write exactly one error-handling function instead of guessing what
 * a failure looks like per endpoint.
 *
 * Without this, Spring's default error body varies by failure type and includes a
 * Java stack trace — which is both unhelpful to a browser and a genuine information
 * leak in production (it tells an attacker your framework versions and class names).
 */
// @JsonInclude(NON_NULL) tells Jackson to OMIT fields that are null rather than
// writing "fieldErrors": null. So a 404 produces a clean three-field object, while
// a validation failure adds the fieldErrors map. One record, two useful shapes.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(

        // The HTTP status code, repeated in the body. It's already in the response
        // headers, but having it in the body means a logged or copy-pasted error is
        // self-describing.
        int status,

        // A human-readable summary: "Recipe 999 was not found".
        String message,

        // Field-by-field validation problems, e.g. {"name": "Recipe name is required"}.
        // NULL for errors that aren't about validation — see @JsonInclude above.
        // This is what lets the frontend show the message under the offending input
        // instead of one vague banner at the top of the form.
        Map<String, String> fieldErrors,

        // When it happened. Useful when a user sends you a screenshot and you need to
        // find the matching line in the server log.
        Instant timestamp) {

    // Factory for the simple case: a status and a message, no field detail.
    public static ApiErrorResponse of(int status, String message) {
        return new ApiErrorResponse(status, message, null, Instant.now());
    }

    // Factory for validation failures, which additionally carry the per-field map.
    public static ApiErrorResponse of(int status, String message, Map<String, String> fieldErrors) {
        return new ApiErrorResponse(status, message, fieldErrors, Instant.now());
    }
}
