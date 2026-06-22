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
    // Bumped by reload() to re-run the single guarded fetch below, so a refresh after a mutation
    // shares the same unmount protection as the initial load.
    const [reloadToken, setReloadToken] = useState(0);

    useEffect(() => {
        let active = true;

        fetchAssets()
            .then((loaded) => {
                if (active) {
                    setAssets(loaded);
                    setError(null);
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
    }, [reloadToken]);

    // Show the loading state and clear any stale error here (an event handler), then bump the
    // token to re-run the guarded fetch — never setting state synchronously inside the effect.
    const reload = useCallback((): void => {
        setLoading(true);
        setError(null);
        setReloadToken((token) => token + 1);
    }, []);

    return { assets, loading, error, reload };
};
