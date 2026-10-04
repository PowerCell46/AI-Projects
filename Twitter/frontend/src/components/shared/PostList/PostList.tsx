import type { PageRequest, TweetItem, TweetPage } from '../../../api/tweetPage';
import NewPostsButton from './NewPostsButton/NewPostsButton';
import PostCell from './PostCell/PostCell';
import PostListStatus from './PostListStatus/PostListStatus';
import type { BottomState } from './PostListStatus/PostListStatus';
import { useBottomSentinel } from './useBottomSentinel';
import { useMinuteClock } from './useMinuteClock';
import { useNewPosts } from './useNewPosts';
import { usePostList } from './usePostList';
import type { PostListState } from './usePostList';
import { useViewTracking } from './useViewTracking';
import './PostList.css';


// A fixed empty list, so the default does not look like a new value on every render.
const NO_OWN_POSTS: TweetItem[] = [];

function bottomStateOf({ status, isEnd, items }: PostListState): BottomState | null {
    if (status === 'loading' || status === 'failed') {
        return status;
    }

    if (isEnd) {
        return items.length === 0 ? 'empty' : 'end';
    }

    return null;
}

interface PostListProps {
    fetchPage: (request: PageRequest) => Promise<TweetPage>;
    endText: string;
    emptyText: string;
    ownPosts?: TweetItem[];
    shouldCheckForNewPosts?: boolean;
}

function PostList({
    fetchPage,
    endText,
    emptyText,
    ownPosts = NO_OWN_POSTS,
    shouldCheckForNewPosts = false,
}: PostListProps) {
    const list = usePostList(fetchPage, ownPosts);
    const now = useMinuteClock();
    const trackView = useViewTracking();
    const sentinelRef = useBottomSentinel(list.loadMore, `${list.items.length}-${list.status}`);
    const hasLoadedFirstPage = list.items.length > 0 || list.isEnd;
    const incoming = useNewPosts(fetchPage, list.loadedItems, list.items, shouldCheckForNewPosts && hasLoadedFirstPage);
    const bottomState = bottomStateOf(list);

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
                        <PostCell post={post} now={now} />
                    </li>
                ))}
            </ul>
            {bottomState && (
                <PostListStatus state={bottomState} endText={endText} emptyText={emptyText} onRetry={list.retry} />
            )}
            <div ref={sentinelRef} />
            <p className="sr-only" aria-live="polite">{list.announcement}</p>
        </>
    );
}

export default PostList;
