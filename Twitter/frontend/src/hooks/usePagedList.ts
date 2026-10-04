import { useEffect, useRef, useState } from 'react';
import type { Identified, Page, PageRequest } from '../api/paging';
import { appendUnique, prependUnique } from '../utils/tweetList';


export type LoadStatus = 'loading' | 'ready' | 'failed';

export interface PagedListState<T extends Identified> {
    loadedItems: T[];
    status: LoadStatus;
    isEnd: boolean;
    announcement: string;
    loadMore: () => void;
    retry: () => void;
    prepend: (newItems: T[]) => void;
    reload: () => void;
}

export const PAGE_SIZE = 20;

// The paging every list shares; only the page fetcher and the announcement text differ. Pages are appended and
// deduplicated by id, one fetch at a time. A page that adds nothing new but has a cursor is followed by the next page
// at once, because a short or empty page is not the end: only a missing cursor is.
export function usePagedList<T extends Identified>(
    fetchPage: (request: PageRequest) => Promise<Page<T>>,
    describeAppend: (addedCount: number) => string,
): PagedListState<T> {
    const [loadedItems, setLoadedItems] = useState<T[]>([]);
    const [status, setStatus] = useState<LoadStatus>('loading');
    const [isEnd, setIsEnd] = useState(false);
    const [announcement, setAnnouncement] = useState('');
    const itemsRef = useRef<T[]>([]);
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

    function prepend(newItems: T[]) {
        itemsRef.current = prependUnique(itemsRef.current, newItems);
        setLoadedItems(itemsRef.current);
    }

    // The one case where the list is replaced instead of extended: the newest page is entirely new, so items may be
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

    return {
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
