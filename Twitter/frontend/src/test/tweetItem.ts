import type { TweetItem } from '../api/tweetPage';


export function tweetItem(fields: Partial<TweetItem> = {}): TweetItem {
    return {
        id: 'tweet-1',
        views: 0,
        savedByMe: false,
        likes: 0,
        likedByMe: false,
        replyCount: 0,
        content: 'a post',
        createdAt: '2026-10-04T11:55:00.000Z',
        updatedAt: '2026-10-04T11:55:00.000Z',
        author: {
            id: 'author-1',
            username: 'ana_b',
            profilePictureUrl: null,
        },
        images: [],
        ...fields,
    };
}
