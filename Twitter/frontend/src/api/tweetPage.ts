import { readJson } from './http';
import type { Page } from './paging';
import { toPictureUrl } from './pictureUrl';


export interface TweetAuthor {
    id: string;
    username: string;
    profilePictureUrl: string | null;
}

export interface TweetImage {
    id: string;
    sizeBytes: number;
    contentType: string;
}

export interface TweetItem {
    id: string;
    views: number;
    savedByMe: boolean;
    likes: number;
    likedByMe: boolean;
    replyCount: number;
    content: string;
    createdAt: string;
    updatedAt: string;
    author: TweetAuthor;
    images: TweetImage[];
}

export type TweetPage = Page<TweetItem>;

export function withAuthorPictureUrl(author: TweetAuthor): TweetAuthor {
    return {
        ...author,
        profilePictureUrl: toPictureUrl(author.profilePictureUrl),
    };
}

export function withPictureUrl(tweet: TweetItem): TweetItem {
    return {
        ...tweet,
        author: withAuthorPictureUrl(tweet.author),
    };
}

export async function readTweetPage(response: Response): Promise<TweetPage> {
    const page = await readJson<TweetPage>(response);

    return {
        ...page,
        items: page.items.map(withPictureUrl),
    };
}
