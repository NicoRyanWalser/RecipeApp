package com.recipeapp.recipeserver.common;

/**
 * Thrown when the client asked for something that does not exist — GET /api/recipes/999
 * when there is no recipe 999.
 *
 * WHY A CUSTOM EXCEPTION INSTEAD OF RETURNING null?
 * Because null forces every caller to remember to check, and the one that forgets
 * produces a NullPointerException 50 lines away from the actual cause. An exception
 * makes the failure impossible to ignore and carries a message explaining itself.
 *
 * The service layer throws this. It does NOT know or care that "not found" means
 * HTTP 404 — services don't know about HTTP. GlobalExceptionHandler is the single
 * place that makes that translation, which is what keeps the layers separate.
 *
 * Extending RuntimeException (rather than Exception) makes this an "unchecked"
 * exception: callers are not forced to declare or catch it. That's the right choice
 * here because there is nothing a caller could usefully DO about it — the only
 * sensible handling is the generic one, and that already exists one layer up.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        // super(...) passes the message up to RuntimeException, which stores it and
        // makes it available via getMessage(). That's what the handler reads.
        super(message);
    }

    /**
     * A convenience factory for the overwhelmingly common case, so call sites read
     * as one short line:  .orElseThrow(() -> NotFoundException.of("Recipe", id))
     * and every 404 message in the app comes out phrased identically.
     */
    public static NotFoundException of(String what, Object id) {
        return new NotFoundException(what + " " + id + " was not found");
    }
}
