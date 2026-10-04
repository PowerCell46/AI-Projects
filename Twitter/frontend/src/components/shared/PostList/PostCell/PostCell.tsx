import type { TweetItem } from '../../../../api/tweetPage';
import { stripBidiControls } from '../../../../utils/bidi';
import { formatPostTime } from '../../../../utils/relativeTime';
import Avatar from './Avatar/Avatar';
import PostActions from './PostActions/PostActions';
import PostImages from './PostImages/PostImages';
import './PostCell.css';


interface PostCellProps {
    post: TweetItem;
    now: Date;
}

function PostCell({ post, now }: PostCellProps) {
    const { author } = post;
    const body = stripBidiControls(post.content);

    return (
        <article className="post-cell" aria-label={`Post by ${author.username}`}>
            <header className="post-cell-author">
                <Avatar userId={author.id} username={author.username} pictureUrl={author.profilePictureUrl} />
                <span className="post-cell-name">{author.username}</span>
                <time className="post-cell-time" dateTime={post.createdAt}>
                    {formatPostTime(post.createdAt, now)}
                </time>
            </header>
            {body && <p className="post-cell-body" dir="auto">{body}</p>}
            <PostImages tweetId={post.id} images={post.images} />
            <PostActions tweetId={post.id} isSavedInitially={post.savedByMe} />
        </article>
    );
}

export default PostCell;
