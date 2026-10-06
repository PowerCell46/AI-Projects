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
    content: string;
    createdAt: string;
    updatedAt: string;
    author: TweetAuthor;
    images: TweetImage[];
}

export type TweetPage = Page<TweetItem>;

function withPictureUrl(tweet: TweetItem): TweetItem {
    return {
        ...tweet,
        author: {
            ...tweet.author,
            profilePictureUrl: toPictureUrl(tweet.author.profilePictureUrl),
        },
    };
}

export async function readTweetPage(response: Response): Promise<TweetPage> {
    const page = await readJson<TweetPage>(response);

    return {
        ...page,
        items: page.items.map(withPictureUrl),
    };
}
