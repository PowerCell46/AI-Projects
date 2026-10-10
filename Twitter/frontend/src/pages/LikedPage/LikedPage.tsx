import { fetchLikedTweets } from '../../api/likes';
import PostList from '../../components/shared/PostList/PostList';
import { useFocusOnMount } from '../../hooks/useFocusOnMount';
import './LikedPage.css';


const END_TEXT = 'END OF LIKED TWEETS';

const EMPTY_TEXT = 'NO LIKED TWEETS YET';

function LikedPage() {
    const headingRef = useFocusOnMount<HTMLHeadingElement>();

    return (
        <>
            <h1 ref={headingRef} className="liked-page-title" tabIndex={-1}>LIKED TWEETS</h1>
            <PostList fetchPage={fetchLikedTweets} endText={END_TEXT} emptyText={EMPTY_TEXT} />
        </>
    );
}

export default LikedPage;
