package com.wealthbuilder.backend.services.interfaces;

import com.wealthbuilder.backend.DTOs.asset.AssetRequest;
import com.wealthbuilder.backend.DTOs.asset.AssetResponse;
import com.wealthbuilder.backend.utils.DataUriImage;

import java.util.List;


/**
 * Manages the moderator-owned asset catalog. Reads are available to any authenticated user;
 * write operations are gated to moderators at the controller via {@code @PreAuthorize}.
 */
public interface AssetService {

    /**
     * All assets for the carousel, without their image blobs. {@code inUse} is computed
     * globally (any user holds it) for moderators, or per-caller (only the requesting user
     * holds it) for regular users — preventing information about other users' holdings leaking.
     */
    List<AssetResponse> findAll(String callerUsername, boolean globalScope);

    /**
     * Single asset (no blob); 404 if the id is unknown. {@code inUse} scoping follows the same
     * rule as {@link #findAll}.
     */
    AssetResponse findById(Long id, String callerUsername, boolean globalScope);

    /**
     * Single asset (no blob) resolved by its name slug; 404 if no name slugifies to the given
     * value. {@code inUse} scoping follows the same rule as {@link #findAll}.
     */
    AssetResponse findBySlug(String slug, String callerUsername, boolean globalScope);

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
     * Deletes an asset; 404 if the id is unknown, 409 if any holding still references it.
     */
    void delete(Long id);
}
