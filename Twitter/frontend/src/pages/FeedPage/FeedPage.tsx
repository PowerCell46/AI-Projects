import { useMemo } from 'react';
import type { Person } from '../../api/accounts/users';
import { fetchFeed } from '../../api/tweets/feed';
import PostList from '../../components/shared/PostList/PostList';
import { useShellContext } from '../../components/shared/Shell/useShellContext';
import { useActiveTab } from '../../hooks/routing/useActiveTab';
import { mergeNewestFirst } from '../../utils/posts/tweetList';
import FeedEmptyPanel from './FeedEmptyPanel/FeedEmptyPanel';
import { useBackfillReloadKey } from './useBackfillReloadKey';
import { useFollowCountWhenShown } from './useFollowCountWhenShown';
import { useFollowedPosts } from './useFollowedPosts';


const END_TEXT = 'END OF FEED';

// Shown only by the plain empty state; the empty feed itself shows `FeedEmptyPanel`, which carries its own text.
const EMPTY_TEXT = 'NOTHING HERE YET — FOLLOW SOMEONE TO SEE THEIR POSTS';

function FeedPage() {
    const { ownPosts, followChangeCount, onFollowChanged } = useShellContext();
    const isShown = useActiveTab() === 'tweets';
    const followCountWhenShown = useFollowCountWhenShown(followChangeCount, isShown);
    const backfillReloadKey = useBackfillReloadKey(followChangeCount, isShown);
    const followedPosts = useFollowedPosts();
    const shownOwnPosts = useMemo(
        () => mergeNewestFirst(ownPosts, followedPosts.posts),
        [ownPosts, followedPosts.posts],
    );

    function handleSuggestionFollowChanged(person: Person, isFollowing: boolean) {
        onFollowChanged();
        followedPosts.handleFollowChanged(person, isFollowing);
    }

    return (
        <>
            <h1 className="sr-only">Feed</h1>
            <PostList
                fetchPage={fetchFeed}
                endText={END_TEXT}
                emptyText={EMPTY_TEXT}
                ownPosts={shownOwnPosts}
                isShown={isShown}
                shouldCheckForNewPosts
                emptyContent={<FeedEmptyPanel onFollowChanged={handleSuggestionFollowChanged} />}
                reloadEmptyKey={followCountWhenShown + backfillReloadKey}
            />
        </>
    );
}

export default FeedPage;
