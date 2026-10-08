import { useState } from 'react';
import type { TweetItem } from '../../api/tweetPage';
import { useShellContext } from '../../components/shared/Shell/useShellContext';


const NO_OWN_POSTS: TweetItem[] = [];

export interface OwnProfilePosts {
    ownPosts: TweetItem[];
    tweetCount: number | null;
}

// On the reader's own profile the posts published this session show at the top, and the server's count (read on open)
// grows by the posts published after the page opened. Posts from before it are already in the server's count. On anyone
// else's profile nothing is added.
export function useOwnProfilePosts(isOwnProfile: boolean, serverTweetCount: number | null): OwnProfilePosts {
    const { ownPosts } = useShellContext();
    const [postsBeforeOpening] = useState(ownPosts.length);

    if (!isOwnProfile) {
        return {
            ownPosts: NO_OWN_POSTS,
            tweetCount: serverTweetCount,
        };
    }

    return {
        ownPosts,
        tweetCount: serverTweetCount === null ? null : serverTweetCount + ownPosts.length - postsBeforeOpening,
    };
}
