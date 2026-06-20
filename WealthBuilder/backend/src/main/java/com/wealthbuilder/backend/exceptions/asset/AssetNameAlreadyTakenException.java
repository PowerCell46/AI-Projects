package com.wealthbuilder.backend.exceptions.asset;


/**
 * Raised when an asset is created or renamed to a name that already exists
 * (case-insensitive). Surfaced as HTTP 409 by the global exception handler.
 */
public class AssetNameAlreadyTakenException extends RuntimeException {

    public AssetNameAlreadyTakenException(String name) {
        super("Asset name already taken: " + name);
    }
}
