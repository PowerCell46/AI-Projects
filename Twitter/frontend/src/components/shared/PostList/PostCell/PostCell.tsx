import type { MouseEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import type { TweetItem } from '../../../../api/tweetPage';
import type { PostUpdate } from '../../../../contexts/PostUpdatesContext';
import { profilePath, tweetPath } from '../../../../routes';
import { stripBidiControls } from '../../../../utils/bidi';
import { hasTextSelection, isInsideElement } from '../../../../utils/clickTarget';
import { formatPostTime } from '../../../../utils/relativeTime';
import Avatar from '../../Avatar/Avatar';
import PostActions from './PostActions/PostActions';
import PostImages from './PostImages/PostImages';
import './PostCell.css';


// A click on any of these keeps its own meaning (like, save, the reply link, an image) instead of opening the post.
const OWN_CLICK_SELECTOR = 'button, a, img';

interface PostCellProps {
    post: TweetItem;
    now: Date;
    isClickable?: boolean;
    onChange?: (update: PostUpdate) => void;
}

function PostCell({ post, now, isClickable = true, onChange }: PostCellProps) {
    const navigate = useNavigate();
    const { author } = post;
    const body = stripBidiControls(post.content);

    function handleClick(event: MouseEvent<HTMLElement>) {
        if (!isClickable || isInsideElement(event.target, OWN_CLICK_SELECTOR) || hasTextSelection()) {
            return;
        }

        navigate(tweetPath(post.id));
    }

    return (
        <article
            className="post-cell"
            aria-label={`Post by ${author.username}`}
            data-clickable={isClickable}
            onClick={handleClick}
        >
            <header className="post-cell-author">
                <Link className="post-cell-author-link" to={profilePath(author.username)}>
                    <Avatar pictureUrl={author.profilePictureUrl} />
                    <span className="post-cell-name">{author.username}</span>
                </Link>
                <time className="post-cell-time" dateTime={post.createdAt}>
                    {formatPostTime(post.createdAt, now)}
                </time>
            </header>
            {body && <p className="post-cell-body" dir="auto">{body}</p>}
            <PostImages tweetId={post.id} images={post.images} />
            <PostActions
                key={`${post.likedByMe}-${post.likes}-${post.savedByMe}`}
                tweetId={post.id}
                isSavedInitially={post.savedByMe}
                isLikedInitially={post.likedByMe}
                likeCount={post.likes}
                replyCount={post.replyCount}
                isReplyLinked={isClickable}
                onChange={onChange}
            />
        </article>
    );
}

export default PostCell;
