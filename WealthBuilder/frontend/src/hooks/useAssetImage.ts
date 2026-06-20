import { useEffect, useState } from 'react';
import { fetchAssetImageBlob } from '../services/assetService';


interface UseAssetImageResult {
    objectUrl: string | null;
    loading: boolean;
    failed: boolean;
}


/**
 * Loads an asset's image through the API client (so the bearer token rides along) and
 * exposes it as an object URL, revoked on cleanup. Replaces the native lazy <img>, which
 * can't carry the Authorization header the image endpoint requires.
 */
export const useAssetImage = (assetId: number): UseAssetImageResult => {
    const [objectUrl, setObjectUrl] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);
    const [failed, setFailed] = useState(false);

    useEffect(() => {
        let active = true;
        let createdUrl: string | null = null;

        fetchAssetImageBlob(assetId)
            .then((blob) => {
                if (!active) {
                    return;
                }

                createdUrl = URL.createObjectURL(blob);
                setObjectUrl(createdUrl);
            })
            .catch(() => {
                if (active) {
                    setFailed(true);
                }
            })
            .finally(() => {
                if (active) {
                    setLoading(false);
                }
            });

        return () => {
            active = false;

            if (createdUrl !== null) {
                URL.revokeObjectURL(createdUrl);
            }
        };
    }, [assetId]);

    return { objectUrl, loading, failed };
};
