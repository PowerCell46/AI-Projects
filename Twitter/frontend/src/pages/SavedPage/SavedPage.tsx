import { fetchSavedTweets } from '../../api/tweets/savedTweets';
import PostList from '../../components/shared/PostList/PostList';
import { useFocusOnMount } from '../../hooks/dom/useFocusOnMount';
import './SavedPage.css';


const END_TEXT = 'END OF SAVED TWEETS';

const EMPTY_TEXT = 'NO SAVED TWEETS YET';

function SavedPage() {
    const headingRef = useFocusOnMount<HTMLHeadingElement>();

    return (
        <>
            <h1 className="saved-page-title" tabIndex={-1} ref={headingRef}>SAVED TWEETS</h1>
            <PostList fetchPage={fetchSavedTweets} endText={END_TEXT} emptyText={EMPTY_TEXT} />
        </>
    );
}

export default SavedPage;
