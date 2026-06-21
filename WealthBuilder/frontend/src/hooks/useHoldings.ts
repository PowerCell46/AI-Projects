import { useCallback, useEffect, useState } from 'react';
import { fetchHoldings, fetchSummary } from '../services/holdingService';
import { EMPTY_HOLDING_FILTER } from '../types/holding';
import type { Holding, HoldingFilter, HoldingSummary } from '../types/holding';
import type { PageResponse } from '../types/page';


export const HOLDINGS_PAGE_SIZE = 10;


interface UseHoldingsResult {
    holdings: PageResponse<Holding> | null;
    summary: HoldingSummary | null;
    pageIndex: number;
    filter: HoldingFilter;
    loading: boolean;
    error: string | null;
    setPageIndex: (page: number) => void;
    setFilter: (filter: HoldingFilter) => void;
    reload: () => void;
}


const isFilterActive = (filter: HoldingFilter): boolean => {
    return filter.name.trim().length > 0 || filter.from.length > 0 || filter.to.length > 0;
};


/**
 * Loads one page of the current user's holdings for an asset, refetching whenever the asset,
 * page, or filter changes. Changing the filter resets to the first page so the user never lands
 * on an out-of-range page. A filter-scoped {@link HoldingSummary} is loaded alongside, but only
 * while a filter is active — unfiltered, {@link UseHoldingsResult.summary} stays null and no
 * summary request is made. {@link UseHoldingsResult.reload} refreshes both after a write.
 */
export const useHoldings = (assetId: number | null): UseHoldingsResult => {
    const [holdings, setHoldings] = useState<PageResponse<Holding> | null>(null);
    const [summary, setSummary] = useState<HoldingSummary | null>(null);
    const [pageIndex, setPageIndex] = useState(0);
    const [filter, setFilterState] = useState<HoldingFilter>(EMPTY_HOLDING_FILTER);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    // State is only set inside the async continuation (or the handlers below), never
    // synchronously in the effect body — that keeps the react-hooks set-state-in-effect rule happy.
    const fetchPage = useCallback(() => {
        if (assetId === null) {
            return;
        }

        fetchHoldings(assetId, pageIndex, HOLDINGS_PAGE_SIZE, filter)
            .then((page) => {
                setHoldings(page);
                setError(null);
            })
            .catch(() => setError('Could not load holdings.'))
            .finally(() => setLoading(false));
    }, [assetId, pageIndex, filter]);

    // The summary is independent of paging (it spans the whole filtered set), so it refetches
    // only on asset/filter changes — not when the user merely pages through the table. It is
    // fetched only while a filter is active; the caller hides it otherwise, so a stale value left
    // over from a since-cleared filter is never shown. setSummary runs only in the async
    // continuation, never synchronously in the effect body (the set-state-in-effect rule).
    const fetchTotals = useCallback(() => {
        if (assetId === null || !isFilterActive(filter)) {
            return;
        }

        fetchSummary(assetId, filter)
            .then(setSummary)
            .catch(() => setSummary(null));
    }, [assetId, filter]);

    useEffect(fetchPage, [fetchPage]);
    useEffect(fetchTotals, [fetchTotals]);

    const changePage = (page: number): void => {
        setLoading(true);
        setPageIndex(page);
    };

    const changeFilter = (next: HoldingFilter): void => {
        setLoading(true);
        setPageIndex(0);
        setFilterState(next);
    };

    const reload = (): void => {
        setLoading(true);
        fetchPage();
        fetchTotals();
    };

    return {
        holdings,
        summary,
        pageIndex,
        filter,
        loading,
        error,
        setPageIndex: changePage,
        setFilter: changeFilter,
        reload,
    };
};
