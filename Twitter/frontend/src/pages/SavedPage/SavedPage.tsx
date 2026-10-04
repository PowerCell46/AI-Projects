import { fetchSavedTweets } from '../../api/savedTweets';
import PostList from '../../components/shared/PostList/PostList';
import './SavedPage.css';


const END_TEXT = 'END OF SAVED TWEETS';

const EMPTY_TEXT = 'NO SAVED TWEETS YET';

function SavedPage() {
    return (
        <>
            <h1 className="saved-page-title">SAVED TWEETS</h1>
            <PostList fetchPage={fetchSavedTweets} endText={END_TEXT} emptyText={EMPTY_TEXT} />
        </>
    );
}

export default SavedPage;
