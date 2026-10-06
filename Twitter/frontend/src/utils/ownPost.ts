import type { AuthUser } from '../api/auth';
import type { PublishedTweet } from '../api/tweets';
import type { TweetItem } from '../api/tweetPage';


// The publish reply has no author and no counters, so the post is completed from the session and the profile picture.
export function toOwnPost(tweet: PublishedTweet, author: AuthUser, pictureUrl: string | null): TweetItem {
    return {
        id: tweet.id,
        views: 0,
        savedByMe: false,
        likes: 0,
        likedByMe: false,
        content: tweet.content,
        createdAt: tweet.createdAt,
        updatedAt: tweet.updatedAt,
        author: {
            id: author.id,
            username: author.username,
            profilePictureUrl: pictureUrl,
        },
        images: tweet.images,
    };
}
