import { useEffect, useState } from 'react';
import { fetchFeed } from '../../../api/feed';
import type { FeedCounts, FeedFilterValue, FeedTopic } from '../../../api/feed';

export const PAGE_SIZE = 20;

const LOADING_INDICATOR_DELAY_MS = 300;

const LOAD_MORE_ERROR = "Couldn't load more topics. Try again.";

export type FeedStatus = 'loading' | 'ready' | 'error';

export interface FeedPages {
    filter: FeedFilterValue;
    topics: FeedTopic[];
    counts: FeedCounts | null;
    status: FeedStatus;
    hasMore: boolean;
    isLoadingMore: boolean;
    loadMoreError: string | null;
    changeFilter: (filter: FeedFilterValue) => void;
    loadMore: () => void;
    retry: () => void;
    applySubscription: (topicId: string, isSubscribed: boolean) => void;
}

export function useFeedPages(): FeedPages {
    const [filter, setFilter] = useState<FeedFilterValue>('ALL');
    const [topics, setTopics] = useState<FeedTopic[]>([]);
    const [nextCursor, setNextCursor] = useState<string | null>(null);
    const [counts, setCounts] = useState<FeedCounts | null>(null);
    const [status, setStatus] = useState<FeedStatus>('loading');
    const [isLoadingMore, setIsLoadingMore] = useState(false);
    const [loadMoreError, setLoadMoreError] = useState<string | null>(null);
    const [reloadToken, setReloadToken] = useState(0);

    useEffect(() => {
        let isCancelled = false;

        // A fast filter switch must not flash "Loading…", so only a slow fetch shows it. A first
        // mount starts as 'loading' and a retry sets it before reloading, so neither waits for this.
        const loadingTimer = window.setTimeout(
            () => setStatus('loading'),
            LOADING_INDICATOR_DELAY_MS,
        );

        fetchFeed(filter, null, PAGE_SIZE)
            .then((page) => {
                if (isCancelled) {
                    return;
                }

                window.clearTimeout(loadingTimer);

                setTopics(page.items);
                setNextCursor(page.nextCursor);
                setCounts(page.counts);
                setStatus('ready');
            })
            .catch(() => {
                if (isCancelled) {
                    return;
                }

                window.clearTimeout(loadingTimer);

                setStatus('error');
            });

        return () => {
            isCancelled = true;

            window.clearTimeout(loadingTimer);
        };
    }, [filter, reloadToken]);

    function changeFilter(nextFilter: FeedFilterValue) {
        setLoadMoreError(null);
        setFilter(nextFilter);
    }

    async function loadMore() {
        if (isLoadingMore || !nextCursor) {
            return;
        }

        setIsLoadingMore(true);
        setLoadMoreError(null);

        try {
            const page = await fetchFeed(filter, nextCursor, PAGE_SIZE);

            setTopics((currentTopics) => [...currentTopics, ...page.items]);
            setNextCursor(page.nextCursor);
            setCounts(page.counts);

        } catch {
            setLoadMoreError(LOAD_MORE_ERROR);

        } finally {
            setIsLoadingMore(false);
        }
    }

    function retry() {
        setLoadMoreError(null);
        setStatus('loading');
        setReloadToken((currentToken) => currentToken + 1);
    }

    function applySubscription(topicId: string, isSubscribed: boolean) {
        setTopics((currentTopics) => currentTopics.map((topic) => (
            topic.id === topicId ? { ...topic, subscribed: isSubscribed } : topic
        )));

        setCounts((currentCounts) => {
            if (!currentCounts) {
                return currentCounts;
            }

            const delta = isSubscribed ? 1 : -1;

            return {
                ...currentCounts,
                subscribed: currentCounts.subscribed + delta,
                notSubscribed: currentCounts.notSubscribed - delta,
            };
        });
    }

    return {
        filter,
        topics,
        counts,
        status,
        hasMore: nextCursor !== null,
        isLoadingMore,
        loadMoreError,
        changeFilter,
        loadMore,
        retry,
        applySubscription,
    };
}
