import { fetchFeed } from '../../api/feed';
import PostList from '../../components/shared/PostList/PostList';
import { useShellContext } from '../../components/shared/Shell/useShellContext';


const END_TEXT = 'END OF FEED';

const EMPTY_TEXT = 'NOTHING HERE YET';

function FeedPage() {
    const { ownPosts } = useShellContext();

    return (
        <>
            <h1 className="sr-only">Feed</h1>
            <PostList
                fetchPage={fetchFeed}
                endText={END_TEXT}
                emptyText={EMPTY_TEXT}
                ownPosts={ownPosts}
                shouldCheckForNewPosts
            />
        </>
    );
}

export default FeedPage;
