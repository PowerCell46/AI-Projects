package com.wealthbuilder.backend.exceptions.asset;


/**
 * Raised when a delete is attempted on an asset that is still referenced by one or more
 * holdings. Surfaced as HTTP 409 by the global exception handler so the catalog never breaks
 * the {@code asset_id} foreign key.
 */
public class AssetInUseException extends RuntimeException {

    public AssetInUseException(Long id) {
        super("Asset is referenced by existing holdings and cannot be deleted: " + id);
    }
}
