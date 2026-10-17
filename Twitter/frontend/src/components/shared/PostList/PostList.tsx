import type { PageRequest } from '../../../api/core/paging';
import type { TweetItem, TweetPage } from '../../../api/tweets/tweetPage';
import { usePostUpdates } from '../../../contexts/PostUpdatesContext';
import { useBottomSentinel } from '../../../hooks/lists/useBottomSentinel';
import { useMinuteClock } from '../../../hooks/dom/useMinuteClock';
import { bottomStateOf } from '../../../utils/posts/bottomState';
import type { EmptyAction } from '../PostListStatus/PostListStatus';
import ListFooter from '../ListFooter/ListFooter';
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
    isShown?: boolean;
}

function PostList({
    fetchPage,
    endText,
    emptyText,
    ownPosts = NO_OWN_POSTS,
    shouldCheckForNewPosts = false,
    emptyAction,
    reloadEmptyKey,
    isShown = true,
}: PostListProps) {
    const { updates } = usePostUpdates();
    const list = usePostList(fetchPage, ownPosts);
    const now = useMinuteClock(isShown);
    const trackView = useViewTracking();
    const sentinelRef = useBottomSentinel(list.loadMore, `${list.items.length}-${list.status}`);
    const hasLoadedFirstPage = list.items.length > 0 || list.isEnd;
    const incoming = useNewPosts(
        fetchPage,
        list.loadedItems,
        list.items,
        shouldCheckForNewPosts && hasLoadedFirstPage && isShown,
    );
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
            <ListFooter
                bottomState={bottomState}
                endText={endText}
                emptyText={emptyText}
                announcement={list.announcement}
                sentinelRef={sentinelRef}
                emptyAction={emptyAction}
                onRetry={list.retry}
            />
        </>
    );
}

export default PostList;
