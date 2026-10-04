import { useNavigate } from 'react-router-dom';
import { fetchFeed } from '../../api/feed';
import PostList from '../../components/shared/PostList/PostList';
import { useShellContext } from '../../components/shared/Shell/useShellContext';
import { useActiveTab } from '../../hooks/useActiveTab';
import { ROUTES } from '../../routes';
import { useFollowCountWhenShown } from './useFollowCountWhenShown';


const END_TEXT = 'END OF FEED';

const EMPTY_TEXT = 'NOTHING HERE YET — FOLLOW SOMEONE TO SEE THEIR POSTS';

const FIND_PEOPLE_LABEL = 'FIND PEOPLE';

function FeedPage() {
    const { ownPosts, followChangeCount } = useShellContext();
    const navigate = useNavigate();
    const isShown = useActiveTab() === 'tweets';
    const followCountWhenShown = useFollowCountWhenShown(followChangeCount, isShown);

    return (
        <>
            <h1 className="sr-only">Feed</h1>
            <PostList
                fetchPage={fetchFeed}
                endText={END_TEXT}
                emptyText={EMPTY_TEXT}
                ownPosts={ownPosts}
                shouldCheckForNewPosts
                emptyAction={{
                    label: FIND_PEOPLE_LABEL,
                    onClick: () => navigate(ROUTES.people),
                }}
                reloadEmptyKey={followCountWhenShown}
            />
        </>
    );
}

export default FeedPage;
