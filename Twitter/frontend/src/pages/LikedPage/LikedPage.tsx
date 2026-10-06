import { fetchLikedTweets } from '../../api/likes';
import PostList from '../../components/shared/PostList/PostList';
import './LikedPage.css';


const END_TEXT = 'END OF LIKED TWEETS';

const EMPTY_TEXT = 'NO LIKED TWEETS YET';

function LikedPage() {
    return (
        <>
            <h1 className="liked-page-title">LIKED TWEETS</h1>
            <PostList fetchPage={fetchLikedTweets} endText={END_TEXT} emptyText={EMPTY_TEXT} />
        </>
    );
}

export default LikedPage;
