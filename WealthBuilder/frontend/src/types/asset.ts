// Domain types mirroring the backend asset contract (see AssetController + DTOs).
// The image blob is never part of these payloads — it is fetched separately from the
// dedicated image endpoint — except on AssetRequest, where moderators submit it.

export interface Asset {
    id: number;
    name: string;
    description: string;
    imageName: string;
    // True when at least one holding (any user) references this asset, so the catalog can
    // disable its delete control — a referenced asset can't be removed.
    inUse: boolean;
}

// Body of POST /api/assets and PUT /api/assets/{id}. `imageBase64` is a
// `data:image/...;base64,...` URI produced by the file picker; `imageName` is the
// original filename of the picked file, kept so the edit form can show it back.
export interface AssetRequest {
    name: string;
    description: string;
    imageBase64: string;
    imageName: string;
}
