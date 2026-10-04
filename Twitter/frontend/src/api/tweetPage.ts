import { readJson } from './http';
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
    content: string;
    createdAt: string;
    updatedAt: string;
    author: TweetAuthor;
    images: TweetImage[];
}

export interface TweetPage {
    items: TweetItem[];
    nextCursor: string | null;
}

export interface PageRequest {
    cursor: string | null;
    size: number;
}

function withPictureUrl(tweet: TweetItem): TweetItem {
    return {
        ...tweet,
        author: {
            ...tweet.author,
            profilePictureUrl: toPictureUrl(tweet.author.profilePictureUrl),
        },
    };
}

export function pageUrl(listUrl: string, request: PageRequest): string {
    const params = new URLSearchParams({ size: String(request.size) });

    if (request.cursor) {
        params.set('cursor', request.cursor);
    }

    return `${listUrl}?${params.toString()}`;
}

export async function readTweetPage(response: Response): Promise<TweetPage> {
    const page = await readJson<TweetPage>(response);

    return {
        ...page,
        items: page.items.map(withPictureUrl),
    };
}
