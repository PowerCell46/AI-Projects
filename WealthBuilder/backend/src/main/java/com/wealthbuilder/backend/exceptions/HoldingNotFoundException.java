package com.wealthbuilder.backend.exceptions;


/**
 * Raised when a holding is requested or mutated by an id that does not exist. Surfaced as
 * HTTP 404 by the global exception handler.
 */
public class HoldingNotFoundException extends RuntimeException {

    public HoldingNotFoundException(Long id) {
        super("Holding not found: " + id);
    }
}
