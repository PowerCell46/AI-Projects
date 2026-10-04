import { likeTweet, unlikeTweet } from '../../../../../api/likes';
import { saveTweet, unsaveTweet } from '../../../../../api/savedTweets';
import FillIcon from './FillIcon/FillIcon';
import { useOptimisticToggle } from './useOptimisticToggle';
import './PostActions.css';


interface PostActionsProps {
    tweetId: string;
    isSavedInitially: boolean;
}

function PostActions({ tweetId, isSavedInitially }: PostActionsProps) {
    const like = useOptimisticToggle(
        false,
        (shouldLike) => (shouldLike ? likeTweet(tweetId) : unlikeTweet(tweetId)),
    );
    const save = useOptimisticToggle(
        isSavedInitially,
        (shouldSave) => (shouldSave ? saveTweet(tweetId) : unsaveTweet(tweetId)),
    );

    // There is no likes service yet, so the only like a post can show is your own.
    const likeCount = like.isOn ? 1 : 0;

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
                <span className="post-actions-count">{likeCount}</span>
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
