package com.wealthbuilder.backend.DTOs.asset;

import com.wealthbuilder.backend.entities.Asset;
import lombok.Value;


/**
 * Asset projection for both the carousel list and the detail view. The image blob is
 * deliberately omitted so list JSON stays small; clients lazy-load it from
 * {@code GET /api/assets/{id}/image}. {@code inUse} reports whether any holding references the
 * asset, so the moderator UI can disable deletion of an asset that can't be removed.
 */
@Value
public class AssetResponse {

    Long id;

    Long version;

    String name;

    String description;

    String imageName;

    boolean inUse;

    public static AssetResponse from(Asset asset, boolean inUse) {
        return new AssetResponse(
                asset.getId(),
                asset.getVersion(),
                asset.getName(),
                asset.getDescription(),
                asset.getImageName(),
                inUse);
    }
}
