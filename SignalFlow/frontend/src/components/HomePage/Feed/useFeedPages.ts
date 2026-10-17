import { useEffect, useRef, useState } from 'react';
import { fetchFeed } from '../../../api/feed';
import type { FeedCounts, FeedFilterValue, FeedPage, FeedTopic } from '../../../api/feed';

export const PAGE_SIZE = 20;

const LOADING_INDICATOR_DELAY_MS = 300;

const LOAD_MORE_ERROR = "Couldn't load more topics. Try again.";

export type FeedStatus = 'loading' | 'ready' | 'error';

export interface FeedPages {
    topics: FeedTopic[];
    counts: FeedCounts | null;
    status: FeedStatus;
    hasMore: boolean;
    isLoadingMore: boolean;
    loadMoreError: string | null;
    loadMore: () => void;
    retry: () => void;
    applySubscription: (topicId: string, isSubscribed: boolean) => void;
}

export function useFeedPages(filter: FeedFilterValue): FeedPages {
    const [topics, setTopics] = useState<FeedTopic[]>([]);
    const [nextCursor, setNextCursor] = useState<string | null>(null);
    const [counts, setCounts] = useState<FeedCounts | null>(null);
    const [status, setStatus] = useState<FeedStatus>('loading');
    const [isLoadingMore, setIsLoadingMore] = useState(false);
    const [loadMoreError, setLoadMoreError] = useState<string | null>(null);
    const [reloadToken, setReloadToken] = useState(0);

    const previousFilterRef = useRef(filter);

    useEffect(() => {
        let isCancelled = false;
        let loadingTimer: number | null = null;

        const hasFilterChanged = previousFilterRef.current !== filter;

        previousFilterRef.current = filter;

        setLoadMoreError(null);

        function clearLoadingTimer() {
            if (loadingTimer !== null) {
                window.clearTimeout(loadingTimer);
            }
        }

        function applyFirstPage(page: FeedPage) {
            setTopics(page.items);
            setNextCursor(page.nextCursor);
            setCounts(page.counts);
            setStatus('ready');
        }

        // A fast filter switch must not flash "Loading…", so only a slow fetch shows it. A first
        // mount or a retry has nothing on screen to keep, so it shows it immediately.
        if (hasFilterChanged) {
            loadingTimer = window.setTimeout(
                () => {
                    if (!isCancelled) {
                        setStatus('loading');
                    }
                },
                LOADING_INDICATOR_DELAY_MS,
            );

        } else {
            setStatus('loading');
        }

        fetchFeed(filter, null, PAGE_SIZE)
            .then((page) => {
                if (isCancelled) {
                    return;
                }

                clearLoadingTimer();
                applyFirstPage(page);
            })
            .catch(() => {
                if (isCancelled) {
                    return;
                }

                clearLoadingTimer();
                setStatus('error');
            });

        return () => {
            isCancelled = true;

            clearLoadingTimer();
        };
    }, [filter, reloadToken]);

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
        topics,
        counts,
        status,
        hasMore: nextCursor !== null,
        isLoadingMore,
        loadMoreError,
        loadMore,
        retry,
        applySubscription,
    };
}
