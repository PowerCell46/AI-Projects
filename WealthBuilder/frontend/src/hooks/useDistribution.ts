import { useEffect, useState } from 'react';
import { fetchDistribution } from '../services/dashboardService';
import type { AssetDistribution } from '../types/dashboard';


interface UseDistributionResult {
    distribution: AssetDistribution[];
    loading: boolean;
    error: string | null;
}


/**
 * Loads the current user's invested-per-asset distribution on mount. Read-only — the home
 * donut never mutates it, so there is no reload callback.
 */
export const useDistribution = (): UseDistributionResult => {
    const [distribution, setDistribution] = useState<AssetDistribution[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let active = true;

        fetchDistribution()
            .then((loaded) => {
                if (active) {
                    setDistribution(loaded);
                }
            })
            .catch(() => {
                if (active) {
                    setError('Could not load distribution.');
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

    return { distribution, loading, error };
};
