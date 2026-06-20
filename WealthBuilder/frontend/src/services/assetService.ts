import { apiRequest, apiRequestBlob } from './apiClient';
import { ASSET_ENDPOINTS } from '../constants/api';
import type { Asset, AssetRequest } from '../types/asset';


// Maps the backend asset endpoints to typed calls. Reads are available to any signed-in
// user; create/update/delete only succeed for moderators (the backend returns 403 otherwise).

export const fetchAssets = (): Promise<Asset[]> => {
    return apiRequest<Asset[]>(ASSET_ENDPOINTS.LIST);
};

export const fetchAsset = (id: number): Promise<Asset> => {
    return apiRequest<Asset>(ASSET_ENDPOINTS.byId(id));
};

export const createAsset = (request: AssetRequest): Promise<Asset> => {
    return apiRequest<Asset>(ASSET_ENDPOINTS.LIST, { method: 'POST', body: request });
};

export const updateAsset = (id: number, request: AssetRequest): Promise<Asset> => {
    return apiRequest<Asset>(ASSET_ENDPOINTS.byId(id), { method: 'PUT', body: request });
};

export const deleteAsset = (id: number): Promise<void> => {
    return apiRequest<void>(ASSET_ENDPOINTS.byId(id), { method: 'DELETE' });
};

/**
 * The raw image bytes for display. The caller wraps the Blob in an object URL and is
 * responsible for revoking it.
 */
export const fetchAssetImageBlob = (id: number): Promise<Blob> => {
    return apiRequestBlob(ASSET_ENDPOINTS.image(id));
};

/**
 * The current image as a `data:` URI, used to prefill the edit form: the list/detail DTOs
 * omit the blob, so the only way to keep an image on edit without re-uploading is to fetch
 * it back and resend it.
 */
export const fetchAssetImageDataUrl = async (id: number): Promise<string> => {
    const blob = await fetchAssetImageBlob(id);

    return blobToDataUrl(blob);
};


const blobToDataUrl = (blob: Blob): Promise<string> => {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();

        reader.onload = () => resolve(reader.result as string);
        reader.onerror = () => reject(reader.error);
        reader.readAsDataURL(blob);
    });
};
