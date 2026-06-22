import { useEffect, useState } from 'react';
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
    // Seed from whether there's an asset to load: with no asset the hook is idle (never stuck
    // 'loading'); with one, the initial fetch below is already in flight.
    const [loading, setLoading] = useState(assetId !== null);
    const [error, setError] = useState<string | null>(null);
    // Bumped by reload() to force a refetch after a write, without re-running the fetchers
    // imperatively (which would race the effects below and risk a stale write).
    const [reloadToken, setReloadToken] = useState(0);

    // Loads the current page, guarded by `active` so a response that arrives after the asset,
    // page, or filter has changed (or the hook unmounted) can't write a stale page into state.
    useEffect(() => {
        if (assetId === null) {
            return;
        }

        let active = true;

        fetchHoldings(assetId, pageIndex, HOLDINGS_PAGE_SIZE, filter)
            .then((page) => {
                if (active) {
                    setHoldings(page);
                    setError(null);
                }
            })
            .catch(() => {
                if (active) {
                    setError('Could not load holdings.');
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
    }, [assetId, pageIndex, filter, reloadToken]);

    // The summary is independent of paging (it spans the whole filtered set), so it refetches
    // only on asset/filter changes — not when the user merely pages through the table. It is
    // fetched only while a filter is active; the caller hides it otherwise, so a stale value left
    // over from a since-cleared filter is never shown.
    useEffect(() => {
        if (assetId === null || !isFilterActive(filter)) {
            return;
        }

        let active = true;

        fetchSummary(assetId, filter)
            .then((next) => {
                if (active) {
                    setSummary(next);
                }
            })
            .catch(() => {
                if (active) {
                    setSummary(null);
                }
            });

        return () => {
            active = false;
        };
    }, [assetId, filter, reloadToken]);

    // The handlers below set the loading flag (and bump the reload token) from event callbacks,
    // so the effect body never has to set state synchronously.
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
        setReloadToken((token) => token + 1);
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
