package com.wealthbuilder.backend.exceptions;


/**
 * Raised when an asset is requested or mutated by an id that does not exist. Surfaced as
 * HTTP 404 by the global exception handler.
 */
public class AssetNotFoundException extends RuntimeException {

    public AssetNotFoundException(Long id) {
        super("Asset not found: " + id);
    }
}
