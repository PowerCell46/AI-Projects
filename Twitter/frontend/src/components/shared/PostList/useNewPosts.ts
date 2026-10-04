import { useEffect, useRef, useState } from 'react';
import type { PageRequest, TweetItem, TweetPage } from '../../../api/tweetPage';
import { findNewerItems } from '../../../utils/tweetList';
import { PAGE_SIZE } from './usePostList';


export const NEW_POSTS_INTERVAL_MS = 60_000;

interface NewPosts {
    newPosts: TweetItem[];
    isWholePageNew: boolean;
    dismissNewPosts: () => void;
}

// Reads the first page again once a minute, while the tab is visible, and keeps the posts that belong above the
// current top post. A failed check is ignored: the next minute tries again.
export function useNewPosts(
    fetchPage: (request: PageRequest) => Promise<TweetPage>,
    loadedItems: TweetItem[],
    shownItems: TweetItem[],
    isEnabled: boolean,
): NewPosts {
    const [newPosts, setNewPosts] = useState<TweetItem[]>([]);
    const loadedItemsRef = useRef(loadedItems);
    const shownItemsRef = useRef(shownItems);
    const isCheckingRef = useRef(false);

    useEffect(() => {
        loadedItemsRef.current = loadedItems;
        shownItemsRef.current = shownItems;
    });

    useEffect(() => {
        if (!isEnabled) {
            return;
        }

        let intervalId: number | undefined;
        let isCancelled = false;

        async function checkForNewPosts() {
            if (isCheckingRef.current) {
                return;
            }

            isCheckingRef.current = true;

            try {
                const firstPage = await fetchPage({
                    cursor: null,
                    size: PAGE_SIZE,
                });

                if (!isCancelled) {
                    const newerPosts = findNewerItems(
                        firstPage.items,
                        loadedItemsRef.current[0],
                        shownItemsRef.current,
                    );

                    setNewPosts(newerPosts);
                }

            } catch {
                // The next check reads the page again.

            } finally {
                isCheckingRef.current = false;
            }
        }

        function startChecking() {
            intervalId = window.setInterval(checkForNewPosts, NEW_POSTS_INTERVAL_MS);
        }

        function stopChecking() {
            window.clearInterval(intervalId);
            intervalId = undefined;
        }

        function handleVisibilityChange() {
            if (document.visibilityState === 'hidden') {
                stopChecking();

            } else if (intervalId === undefined) {
                startChecking();
            }
        }

        if (document.visibilityState !== 'hidden') {
            startChecking();
        }

        document.addEventListener('visibilitychange', handleVisibilityChange);

        return () => {
            isCancelled = true;
            stopChecking();
            document.removeEventListener('visibilitychange', handleVisibilityChange);
        };
    }, [fetchPage, isEnabled]);

    return {
        newPosts,
        isWholePageNew: newPosts.length >= PAGE_SIZE,
        dismissNewPosts: () => setNewPosts([]),
    };
}
