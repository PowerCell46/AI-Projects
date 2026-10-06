import { describe, expect, it } from 'vitest';
import type { PublishedTweet } from '../api/tweets';
import { toOwnPost } from './ownPost';


const AUTHOR = {
    id: 'user-1',
    username: 'peter_g',
    email: 'peter@example.com',
};

const TWEET: PublishedTweet = {
    id: 'tweet-1',
    authorId: 'user-1',
    content: 'hello',
    createdAt: '2026-10-04T12:00:00.123Z',
    updatedAt: '2026-10-04T12:00:00.456Z',
    images: [
        {
            id: 'image-1',
            sizeBytes: 12,
            contentType: 'image/png',
        },
    ],
};

describe('toOwnPost', () => {
    it('should_copy_the_tweet_fields_from_the_publish_reply', () => {
        const post = toOwnPost(TWEET, AUTHOR, null);

        expect(post).toMatchObject({
            id: 'tweet-1',
            content: 'hello',
            createdAt: '2026-10-04T12:00:00.123Z',
            updatedAt: '2026-10-04T12:00:00.456Z',
            images: TWEET.images,
        });
    });

    it('should_take_the_author_from_the_session_and_the_picture_from_the_profile', () => {
        const post = toOwnPost(TWEET, AUTHOR, 'http://localhost/api/v1/files/pic-1');

        expect(post.author).toEqual({
            id: 'user-1',
            username: 'peter_g',
            profilePictureUrl: 'http://localhost/api/v1/files/pic-1',
        });
    });

    it('should_not_carry_the_email_into_the_post', () => {
        const post = toOwnPost(TWEET, AUTHOR, null);

        expect(JSON.stringify(post)).not.toContain('peter@example.com');
    });

    it('should_start_with_no_views_no_likes_and_not_saved_or_liked', () => {
        const post = toOwnPost(TWEET, AUTHOR, null);

        expect(post.views).toBe(0);
        expect(post.likes).toBe(0);
        expect(post.savedByMe).toBe(false);
        expect(post.likedByMe).toBe(false);
    });

    it('should_leave_the_picture_empty_when_the_profile_has_none_yet', () => {
        expect(toOwnPost(TWEET, AUTHOR, null).author.profilePictureUrl).toBeNull();
    });
});
