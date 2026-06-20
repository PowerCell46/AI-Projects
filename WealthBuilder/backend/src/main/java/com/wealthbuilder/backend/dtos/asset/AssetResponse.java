package com.wealthbuilder.backend.dtos.asset;

import com.wealthbuilder.backend.entities.Asset;
import lombok.Value;


/**
 * Asset projection for both the carousel list and the detail view. The image blob is
 * deliberately omitted so list JSON stays small; clients lazy-load it from
 * {@code GET /api/assets/{id}/image}.
 */
@Value
public class AssetResponse {

    Long id;

    String name;

    String description;

    String imageName;

    public static AssetResponse from(Asset asset) {
        return new AssetResponse(
                asset.getId(),
                asset.getName(),
                asset.getDescription(),
                asset.getImageName());
    }
}
