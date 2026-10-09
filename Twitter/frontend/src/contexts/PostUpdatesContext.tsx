import { createContext, useContext, useState } from 'react';
import type { ReactNode } from 'react';
import type { TweetItem } from '../api/tweetPage';


// The fields of a post that you can change from its details page.
export type PostUpdate = Partial<Pick<TweetItem, 'likedByMe' | 'likes' | 'savedByMe' | 'replyCount'>>;

interface PostUpdatesContextValue {
    updates: Record<string, PostUpdate>;
    reportUpdate: (tweetId: string, update: PostUpdate) => void;
}

interface PostUpdatesProviderProps {
    children: ReactNode;
}

const PostUpdatesContext = createContext<PostUpdatesContextValue | null>(null);

export function PostUpdatesProvider({ children }: PostUpdatesProviderProps) {
    const [updates, setUpdates] = useState<Record<string, PostUpdate>>({});

    function reportUpdate(tweetId: string, update: PostUpdate) {
        setUpdates((currentUpdates) => ({
            ...currentUpdates,
            [tweetId]: {
                ...currentUpdates[tweetId],
                ...update,
            },
        }));
    }

    return (
        <PostUpdatesContext.Provider value={{ updates, reportUpdate }}>
            {children}
        </PostUpdatesContext.Provider>
    );
}

// oxlint-disable-next-line react/only-export-components
export function usePostUpdates(): PostUpdatesContextValue {
    const context = useContext(PostUpdatesContext);

    if (!context) {
        throw new Error('usePostUpdates must be used inside PostUpdatesProvider.');
    }

    return context;
}
