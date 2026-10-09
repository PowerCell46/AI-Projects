import type { PageRequest } from '../../../api/paging';
import type { TweetItem, TweetPage } from '../../../api/tweetPage';
import { usePostUpdates } from '../../../contexts/PostUpdatesContext';
import { useBottomSentinel } from '../../../hooks/useBottomSentinel';
import { useMinuteClock } from '../../../hooks/useMinuteClock';
import { bottomStateOf } from '../../../utils/bottomState';
import PostListStatus from '../PostListStatus/PostListStatus';
import type { EmptyAction } from '../PostListStatus/PostListStatus';
import NewPostsButton from './NewPostsButton/NewPostsButton';
import PostCell from './PostCell/PostCell';
import { useNewPosts } from './useNewPosts';
import { usePostList } from './usePostList';
import { useReloadEmptyList } from './useReloadEmptyList';
import { useViewTracking } from './useViewTracking';
import './PostList.css';


// A fixed empty list, so the default does not look like a new value on every render.
const NO_OWN_POSTS: TweetItem[] = [];

interface PostListProps {
    fetchPage: (request: PageRequest) => Promise<TweetPage>;
    endText: string;
    emptyText: string;
    ownPosts?: TweetItem[];
    shouldCheckForNewPosts?: boolean;
    emptyAction?: EmptyAction;
    reloadEmptyKey?: number;
}

function PostList({
    fetchPage,
    endText,
    emptyText,
    ownPosts = NO_OWN_POSTS,
    shouldCheckForNewPosts = false,
    emptyAction,
    reloadEmptyKey,
}: PostListProps) {
    const list = usePostList(fetchPage, ownPosts);
    const { updates } = usePostUpdates();
    const now = useMinuteClock();
    const trackView = useViewTracking();
    const sentinelRef = useBottomSentinel(list.loadMore, `${list.items.length}-${list.status}`);
    const hasLoadedFirstPage = list.items.length > 0 || list.isEnd;
    const incoming = useNewPosts(fetchPage, list.loadedItems, list.items, shouldCheckForNewPosts && hasLoadedFirstPage);
    const bottomState = bottomStateOf(list.status, list.isEnd, list.items.length);

    const isEmpty = list.items.length === 0 && list.isEnd;

    useReloadEmptyList(isEmpty, list.reload, reloadEmptyKey);

    function handleShowNewPosts() {
        if (incoming.isWholePageNew) {
            list.reload();

        } else {
            list.prepend(incoming.newPosts);
        }

        incoming.dismissNewPosts();
        window.scrollTo({ top: 0 });
    }

    return (
        <>
            {incoming.newPosts.length > 0 && <NewPostsButton onClick={handleShowNewPosts} />}
            <ul className="post-list">
                {list.items.map((post) => (
                    <li key={post.id} data-tweet-id={post.id} ref={trackView}>
                        <PostCell
                            post={{ ...post, ...updates[post.id] }}
                            now={now}
                        />
                    </li>
                ))}
            </ul>
            {bottomState && (
                <PostListStatus
                    state={bottomState}
                    endText={endText}
                    emptyText={emptyText}
                    emptyAction={emptyAction}
                    onRetry={list.retry}
                />
            )}
            <div ref={sentinelRef} />
            <p className="sr-only" aria-live="polite">{list.announcement}</p>
        </>
    );
}

export default PostList;
