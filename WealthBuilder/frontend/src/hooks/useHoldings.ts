import { useCallback, useEffect, useState } from 'react';
import { fetchHoldings, fetchHoldingSummary } from '../services/holdingService';
import type { Holding, HoldingSummary } from '../types/holding';
import type { PageResponse } from '../types/page';


export const HOLDINGS_PAGE_SIZE = 10;


interface UseHoldingsResult {
    holdings: PageResponse<Holding> | null;
    summary: HoldingSummary | null;
    pageIndex: number;
    loading: boolean;
    error: string | null;
    setPageIndex: (page: number) => void;
    reload: () => void;
}


/**
 * Loads one page of the current user's holdings for an asset plus the (whole-set) aggregation
 * summary, refetching whenever the asset or page changes. {@link UseHoldingsResult.reload} lets
 * callers refresh both after a create/edit/delete.
 */
export const useHoldings = (assetId: number | null): UseHoldingsResult => {
    const [holdings, setHoldings] = useState<PageResponse<Holding> | null>(null);
    const [summary, setSummary] = useState<HoldingSummary | null>(null);
    const [pageIndex, setPageIndex] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    // State is only ever set inside the async continuations (or the event handlers below), never
    // synchronously in the effect body — that keeps the react-hooks set-state-in-effect rule happy.
    const fetchPage = useCallback(() => {
        if (assetId === null) {
            return;
        }

        Promise
            .all([fetchHoldings(assetId, pageIndex, HOLDINGS_PAGE_SIZE), fetchHoldingSummary(assetId)])
            .then(([page, loadedSummary]) => {
                setHoldings(page);
                setSummary(loadedSummary);
                setError(null);
            })
            .catch(() => setError('Could not load holdings.'))
            .finally(() => setLoading(false));
    }, [assetId, pageIndex]);

    useEffect(fetchPage, [fetchPage]);

    const changePage = (page: number): void => {
        setLoading(true);
        setPageIndex(page);
    };

    const reload = (): void => {
        setLoading(true);
        fetchPage();
    };

    return { holdings, summary, pageIndex, loading, error, setPageIndex: changePage, reload };
};
