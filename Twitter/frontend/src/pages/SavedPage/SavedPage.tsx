import { fetchSavedTweets } from '../../api/savedTweets';
import PostList from '../../components/shared/PostList/PostList';
import { useFocusOnMount } from '../../hooks/useFocusOnMount';
import './SavedPage.css';


const END_TEXT = 'END OF SAVED TWEETS';

const EMPTY_TEXT = 'NO SAVED TWEETS YET';

function SavedPage() {
    const headingRef = useFocusOnMount<HTMLHeadingElement>();

    return (
        <>
            <h1 ref={headingRef} className="saved-page-title" tabIndex={-1}>SAVED TWEETS</h1>
            <PostList fetchPage={fetchSavedTweets} endText={END_TEXT} emptyText={EMPTY_TEXT} />
        </>
    );
}

export default SavedPage;
