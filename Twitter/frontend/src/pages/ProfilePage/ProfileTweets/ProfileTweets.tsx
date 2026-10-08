import { useCallback } from 'react';
import { fetchAuthorTweets } from '../../../api/authorTweets';
import type { PageRequest } from '../../../api/paging';
import type { TweetItem } from '../../../api/tweetPage';
import PostList from '../../../components/shared/PostList/PostList';
import { formatFullCount } from '../../../utils/profileFormat';
import './ProfileTweets.css';


const END_TEXT = 'END OF TWEETS';

const EMPTY_TEXT = 'NO TWEETS YET';

interface ProfileTweetsProps {
    authorId: string;
    tweetCount: number | null;
    ownPosts: TweetItem[];
}

function ProfileTweets({ authorId, tweetCount, ownPosts }: ProfileTweetsProps) {
    const fetchPage = useCallback(
        (request: PageRequest) => fetchAuthorTweets(authorId, request),
        [authorId],
    );

    return (
        <section aria-labelledby="profile-tweets-heading">
            <h2 id="profile-tweets-heading" className="profile-tweets-heading">
                {tweetCount === null ? 'TWEETS' : `TWEETS · ${formatFullCount(tweetCount)}`}
            </h2>
            <PostList
                fetchPage={fetchPage}
                endText={END_TEXT}
                emptyText={EMPTY_TEXT}
                ownPosts={ownPosts}
            />
        </section>
    );
}

export default ProfileTweets;
