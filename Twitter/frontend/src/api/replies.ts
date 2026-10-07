import { ENDPOINTS } from './endpoints';
import { jsonRequest, readJson, sendAuthenticated } from './http';
import { pageUrl } from './paging';
import type { Page, PageRequest } from './paging';
import { withAuthorPictureUrl } from './tweetPage';
import type { TweetAuthor } from './tweetPage';


export interface Reply {
    id: string;
    tweetId: string;
    content: string;
    edited: boolean;
    createdAt: string;
    updatedAt: string;
    author: TweetAuthor;
}

export type ReplyPage = Page<Reply>;

function withPictureUrl(reply: Reply): Reply {
    return {
        ...reply,
        author: withAuthorPictureUrl(reply.author),
    };
}

export async function fetchReplies(tweetId: string, request: PageRequest): Promise<ReplyPage> {
    const response = await sendAuthenticated(
        pageUrl(ENDPOINTS.replies(tweetId), request),
        { method: 'GET' },
    );

    const page = await readJson<ReplyPage>(response);

    return {
        ...page,
        items: page.items.map(withPictureUrl),
    };
}

export async function createReply(tweetId: string, content: string): Promise<Reply> {
    const response = await sendAuthenticated(
        ENDPOINTS.replies(tweetId),
        jsonRequest('POST', { content }),
    );

    return withPictureUrl(await readJson<Reply>(response));
}

export async function updateReply(tweetId: string, replyId: string, content: string): Promise<Reply> {
    const response = await sendAuthenticated(
        ENDPOINTS.reply(tweetId, replyId),
        jsonRequest('PUT', { content }),
    );

    return withPictureUrl(await readJson<Reply>(response));
}

export async function deleteReply(tweetId: string, replyId: string): Promise<void> {
    await sendAuthenticated(
        ENDPOINTS.reply(tweetId, replyId),
        { method: 'DELETE' },
    );
}
