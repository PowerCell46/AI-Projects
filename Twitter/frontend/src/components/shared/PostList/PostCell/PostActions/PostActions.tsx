import { likeTweet, unlikeTweet } from '../../../../../api/likes';
import { saveTweet, unsaveTweet } from '../../../../../api/savedTweets';
import { useOptimisticToggle } from '../../../../../hooks/useOptimisticToggle';
import { formatCount } from '../../../../../utils/count';
import FillIcon from './FillIcon/FillIcon';
import './PostActions.css';


interface PostActionsProps {
    tweetId: string;
    isSavedInitially: boolean;
    isLikedInitially: boolean;
    likeCount: number;
}

function PostActions({ tweetId, isSavedInitially, isLikedInitially, likeCount }: PostActionsProps) {
    const like = useOptimisticToggle(
        isLikedInitially,
        (shouldLike) => (shouldLike ? likeTweet(tweetId) : unlikeTweet(tweetId)),
    );
    const save = useOptimisticToggle(
        isSavedInitially,
        (shouldSave) => (shouldSave ? saveTweet(tweetId) : unsaveTweet(tweetId)),
    );

    // The server's count already holds your like when the post loaded liked; a read taken mid-race can say 0 for a
    // post that is liked by you, so the shown count never goes below 0.
    const shownLikeCount = Math.max(0, likeCount - Number(isLikedInitially) + Number(like.isOn));

    return (
        <footer className="post-actions">
            <button
                type="button"
                className="post-actions-button"
                data-action="like"
                data-active={like.isOn}
                aria-pressed={like.isOn}
                onClick={like.toggle}
            >
                <span className="post-actions-icon">
                    <FillIcon shape="heart" isActive={like.isOn} />
                </span>
                <span className="sr-only">Like</span>
                <span className="post-actions-count">{formatCount(shownLikeCount)}</span>
            </button>
            <button
                type="button"
                className="post-actions-button"
                data-action="save"
                data-active={save.isOn}
                aria-pressed={save.isOn}
                onClick={save.toggle}
            >
                <span className="post-actions-icon">
                    <FillIcon shape="bookmark" isActive={save.isOn} />
                </span>
                <span className="sr-only">Save</span>
            </button>
        </footer>
    );
}

export default PostActions;
