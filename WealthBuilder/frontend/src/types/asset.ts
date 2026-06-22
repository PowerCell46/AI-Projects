// Domain types mirroring the backend asset contract (see AssetController + DTOs).
// The image blob is never part of these payloads — it is fetched separately from the
// dedicated image endpoint — except on AssetRequest, where moderators submit it.

export interface Asset {
    id: number;
    version: number;
    name: string;
    description: string;
    imageName: string;
    // For moderators: true when any user holds this asset (used to disable the delete button).
    // For regular users: true when the requesting user personally holds this asset.
    inUse: boolean;
}

// Body of POST /api/assets and PUT /api/assets/{id}. `imageBase64` is a
// `data:image/...;base64,...` URI produced by the file picker; `imageName` is the
// original filename of the picked file, kept so the edit form can show it back.
// `version` is omitted on create; on update it echoes the version received from the last
// read so the backend can detect and reject stale-form overwrites.
export interface AssetRequest {
    version?: number;
    name: string;
    description: string;
    imageBase64: string;
    imageName: string;
}
