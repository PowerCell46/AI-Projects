import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { TweetItem } from '../api/tweets/tweetPage';


// The fields of a post that you can change from its details page.
export type PostUpdate = Partial<Pick<TweetItem, 'likedByMe' | 'likes' | 'savedByMe' | 'replyCount'>>;

interface PostUpdatesContextValue {
    updates: Record<string, PostUpdate>;
    reportUpdate: (tweetId: string, update: PostUpdate) => void;
}

const PostUpdatesContext = createContext<PostUpdatesContextValue | null>(null);

interface PostUpdatesProviderProps {
    children: ReactNode;
}

export function PostUpdatesProvider({ children }: PostUpdatesProviderProps) {
    const [updates, setUpdates] = useState<Record<string, PostUpdate>>({});

    const reportUpdate = useCallback((tweetId: string, update: PostUpdate) => {
        setUpdates((currentUpdates) => ({
            ...currentUpdates,
            [tweetId]: {
                ...currentUpdates[tweetId],
                ...update,
            },
        }));
    }, []);

    const value = useMemo(
        () => ({
            updates,
            reportUpdate,
        }),
        [updates, reportUpdate],
    );

    return (
        <PostUpdatesContext.Provider value={value}>
            {children}
        </PostUpdatesContext.Provider>
    );
}

// The context convention keeps the provider and its hook in one file, so fast refresh reloads the page here.
// oxlint-disable-next-line react/only-export-components
export function usePostUpdates(): PostUpdatesContextValue {
    const context = useContext(PostUpdatesContext);

    if (!context) {
        throw new Error('usePostUpdates must be used inside PostUpdatesProvider.');
    }

    return context;
}
