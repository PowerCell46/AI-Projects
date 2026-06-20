package com.wealthbuilder.backend.services.interfaces;

import com.wealthbuilder.backend.dtos.asset.AssetRequest;
import com.wealthbuilder.backend.dtos.asset.AssetResponse;
import com.wealthbuilder.backend.utils.DataUriImage;

import java.util.List;


/**
 * Manages the moderator-owned asset catalog. Reads are available to any authenticated user;
 * write operations are gated to moderators at the controller via {@code @PreAuthorize}.
 */
public interface AssetService {

    /**
     * All assets for the carousel, without their image blobs.
     */
    List<AssetResponse> findAll();

    /**
     * Single asset (no blob); 404 if the id is unknown.
     */
    AssetResponse findById(Long id);

    /**
     * Decoded image bytes for the dedicated image endpoint; 404 if the id is unknown.
     */
    DataUriImage findImage(Long id);

    /**
     * Creates an asset; 409 if the name already exists (case-insensitive).
     */
    AssetResponse create(AssetRequest request);

    /**
     * Updates an asset; 404 if unknown, 409 if the new name collides with another asset.
     */
    AssetResponse update(Long id, AssetRequest request);

    /**
     * Deletes an asset; 404 if the id is unknown.
     */
    void delete(Long id);
}
