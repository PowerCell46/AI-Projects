import { useCallback, useEffect, useState } from 'react';
import { fetchHoldings } from '../services/holdingService';
import { EMPTY_HOLDING_FILTER } from '../types/holding';
import type { Holding, HoldingFilter } from '../types/holding';
import type { PageResponse } from '../types/page';


export const HOLDINGS_PAGE_SIZE = 10;


interface UseHoldingsResult {
    holdings: PageResponse<Holding> | null;
    pageIndex: number;
    filter: HoldingFilter;
    loading: boolean;
    error: string | null;
    setPageIndex: (page: number) => void;
    setFilter: (filter: HoldingFilter) => void;
    reload: () => void;
}


/**
 * Loads one page of the current user's holdings for an asset, refetching whenever the asset,
 * page, or filter changes. Changing the filter resets to the first page so the user never lands
 * on an out-of-range page. {@link UseHoldingsResult.reload} refreshes after a create/edit/delete.
 */
export const useHoldings = (assetId: number | null): UseHoldingsResult => {
    const [holdings, setHoldings] = useState<PageResponse<Holding> | null>(null);
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

    useEffect(fetchPage, [fetchPage]);

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
    };

    return {
        holdings,
        pageIndex,
        filter,
        loading,
        error,
        setPageIndex: changePage,
        setFilter: changeFilter,
        reload,
    };
};
