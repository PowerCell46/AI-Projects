import { useEffect, useState } from 'react';
import { fetchAssetBySlug } from '../services/assetService';
import type { Asset } from '../types/asset';


interface UseAssetResult {
    asset: Asset | null;
    loading: boolean;
    error: string | null;
}


/**
 * Loads a single asset by its name slug, so the detail page can deep-link by a readable URL
 * without fetching the whole catalog. The request is guarded by `active` so a response that
 * arrives after the slug changed (or the component unmounted) can't write a stale asset.
 */
export const useAsset = (slug: string | undefined): UseAssetResult => {
    const [asset, setAsset] = useState<Asset | null>(null);
    const [loading, setLoading] = useState(slug !== undefined);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (slug === undefined) {
            return;
        }

        let active = true;

        fetchAssetBySlug(slug)
            .then((loaded) => {
                if (active) {
                    setAsset(loaded);
                    setError(null);
                }
            })
            .catch(() => {
                if (active) {
                    setError('Asset not found.');
                }
            })
            .finally(() => {
                if (active) {
                    setLoading(false);
                }
            });

        return () => {
            active = false;
        };
    }, [slug]);

    return { asset, loading, error };
};
