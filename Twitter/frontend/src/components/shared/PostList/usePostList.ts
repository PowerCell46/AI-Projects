import { useEffect, useMemo, useRef, useState } from 'react';
import type { PageRequest, TweetItem, TweetPage } from '../../../api/tweetPage';
import { appendUnique, mergeNewestFirst, prependUnique } from '../../../utils/tweetList';


export type LoadStatus = 'loading' | 'ready' | 'failed';

export interface PostListState {
    items: TweetItem[];
    loadedItems: TweetItem[];
    status: LoadStatus;
    isEnd: boolean;
    announcement: string;
    loadMore: () => void;
    retry: () => void;
    prepend: (newItems: TweetItem[]) => void;
    reload: () => void;
}

export const PAGE_SIZE = 20;

function describeAppend(addedCount: number): string {
    return `${addedCount} more ${addedCount === 1 ? 'post' : 'posts'} loaded`;
}

// One list for both pages; only the page fetcher differs. Pages are appended and deduplicated by id, one fetch at a
// time. A page that adds nothing new but has a cursor is followed by the next page at once, because a short or empty
// page is not the end: only a missing cursor is. `ownPosts` are the reader's own new posts: they are shown in their
// place by time until the server delivers the same id, and then the loaded copy wins.
export function usePostList(
    fetchPage: (request: PageRequest) => Promise<TweetPage>,
    ownPosts: TweetItem[],
): PostListState {
    const [loadedItems, setLoadedItems] = useState<TweetItem[]>([]);
    const [status, setStatus] = useState<LoadStatus>('loading');
    const [isEnd, setIsEnd] = useState(false);
    const [announcement, setAnnouncement] = useState('');
    const itemsRef = useRef<TweetItem[]>([]);
    const cursorRef = useRef<string | null>(null);
    const isEndRef = useRef(false);
    const isFetchingRef = useRef(false);
    const hasFailedRef = useRef(false);
    const isActiveRef = useRef(false);
    // Bumped by a reload, so a request still on its way from before it is thrown away when it lands.
    const generationRef = useRef(0);

    async function fetchUntilSomethingIsAdded(generation: number) {
        let hasAddedItems = false;

        while (!hasAddedItems && !isEndRef.current) {
            const page = await fetchPage({
                cursor: cursorRef.current,
                size: PAGE_SIZE,
            });

            if (!isActiveRef.current || generation !== generationRef.current) {
                return;
            }

            const previousCount = itemsRef.current.length;
            itemsRef.current = appendUnique(itemsRef.current, page.items);
            cursorRef.current = page.nextCursor;
            isEndRef.current = page.nextCursor === null;
            hasAddedItems = itemsRef.current.length > previousCount;

            setLoadedItems(itemsRef.current);
            setIsEnd(isEndRef.current);

            if (hasAddedItems && previousCount > 0) {
                setAnnouncement(describeAppend(itemsRef.current.length - previousCount));
            }
        }
    }

    async function load() {
        if (isFetchingRef.current || isEndRef.current || hasFailedRef.current) {
            return;
        }

        const generation = generationRef.current;
        isFetchingRef.current = true;
        setStatus('loading');
        setAnnouncement('');

        try {
            await fetchUntilSomethingIsAdded(generation);

            if (generation === generationRef.current) {
                setStatus('ready');
            }

        } catch {
            if (generation === generationRef.current) {
                hasFailedRef.current = true;
                setStatus('failed');
            }

        } finally {
            if (generation === generationRef.current) {
                isFetchingRef.current = false;
            }
        }
    }

    function retry() {
        hasFailedRef.current = false;
        load();
    }

    function prepend(newItems: TweetItem[]) {
        itemsRef.current = prependUnique(itemsRef.current, newItems);
        setLoadedItems(itemsRef.current);
    }

    // The one case where the list is replaced instead of extended: the newest page is entirely new, so posts may be
    // missing between it and what is on screen.
    function reload() {
        generationRef.current += 1;
        itemsRef.current = [];
        cursorRef.current = null;
        isEndRef.current = false;
        isFetchingRef.current = false;
        hasFailedRef.current = false;
        setLoadedItems([]);
        setIsEnd(false);

        load();
    }

    // The first page is read on mount. The flag is set again on every mount, so React's development double-mount
    // does not drop the first answer, and `load` is meant to be read fresh only once, so it stays out of the deps.
    useEffect(() => {
        isActiveRef.current = true;
        // oxlint-disable-next-line react/set-state-in-effect
        load();

        return () => {
            isActiveRef.current = false;
        };
        // oxlint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const items = useMemo(
        () => mergeNewestFirst(loadedItems, ownPosts),
        [loadedItems, ownPosts],
    );

    return {
        items,
        loadedItems,
        status,
        isEnd,
        announcement,
        loadMore: load,
        retry,
        prepend,
        reload,
    };
}
