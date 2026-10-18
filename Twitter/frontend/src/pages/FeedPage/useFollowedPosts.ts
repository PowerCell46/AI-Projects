import { useMemo, useRef, useState } from 'react';
import type { Person } from '../../api/accounts/users';
import { fetchAuthorTweets } from '../../api/tweets/authorTweets';
import type { TweetItem } from '../../api/tweets/tweetPage';
import { mergeNewestFirst } from '../../utils/posts/tweetList';


const FOLLOWED_POSTS_SIZE = 10;

export interface FollowedPosts {
    posts: TweetItem[];
    handleFollowChanged: (person: Person, isFollowing: boolean) => void;
}

// The latest posts of the people followed from the empty feed's suggestions, newest first. The server only back-fills
// the feed some seconds after a follow, so these are shown at once and dropped when the server delivers the same ids.
// Unfollowing takes a person's posts away again; a read that lands after the unfollow is thrown away.
export function useFollowedPosts(): FollowedPosts {
    const [postsByAuthorId, setPostsByAuthorId] = useState<Record<string, TweetItem[]>>({});
    const followedAuthorIdsRef = useRef(new Set<string>());

    const posts = useMemo(
        () => Object.values(postsByAuthorId).reduce(mergeNewestFirst, []),
        [postsByAuthorId],
    );

    function forgetPosts(authorId: string) {
        setPostsByAuthorId((currentPosts) => {
            const remainingPosts = { ...currentPosts };

            delete remainingPosts[authorId];

            return remainingPosts;
        });
    }

    function readPosts(authorId: string) {
        fetchAuthorTweets(authorId, {
            cursor: null,
            size: FOLLOWED_POSTS_SIZE,
        })
            .then((page) => {
                if (followedAuthorIdsRef.current.has(authorId)) {
                    setPostsByAuthorId((currentPosts) => ({
                        ...currentPosts,
                        [authorId]: page.items,
                    }));
                }
            })
            // The server's back-fill still delivers them, only later.
            .catch(() => undefined);
    }

    function handleFollowChanged(person: Person, isFollowing: boolean) {
        if (isFollowing) {
            followedAuthorIdsRef.current.add(person.id);
            readPosts(person.id);

        } else {
            followedAuthorIdsRef.current.delete(person.id);
            forgetPosts(person.id);
        }
    }

    return {
        posts,
        handleFollowChanged,
    };
}
