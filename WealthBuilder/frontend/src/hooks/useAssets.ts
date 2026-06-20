import { useCallback, useEffect, useState } from 'react';
import { fetchAssets } from '../services/assetService';
import type { Asset } from '../types/asset';


interface UseAssetsResult {
    assets: Asset[];
    loading: boolean;
    error: string | null;
    reload: () => void;
}


/**
 * Loads the asset catalog on mount and exposes a {@link UseAssetsResult.reload} callback so
 * callers (the admin screen) can refresh after a mutation.
 */
export const useAssets = (): UseAssetsResult => {
    const [assets, setAssets] = useState<Asset[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const load = useCallback(() => {
        setLoading(true);
        setError(null);

        return fetchAssets()
            .then(setAssets)
            .catch(() => setError('Could not load assets.'))
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        let active = true;

        fetchAssets()
            .then((loaded) => {
                if (active) {
                    setAssets(loaded);
                }
            })
            .catch(() => {
                if (active) {
                    setError('Could not load assets.');
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
    }, []);

    return { assets, loading, error, reload: load };
};
