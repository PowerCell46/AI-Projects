import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/http';
import { createReply, deleteReply, fetchReplies, updateReply } from '../../api/replies';
import { fetchTweetDetails } from '../../api/tweetDetails';
import { intersect } from '../../test/intersectionObserver';
import { viewReporter } from '../../utils/viewReporter';
import { ROUTES } from '../../routes';
import TweetDetailsPage from './TweetDetailsPage';


vi.mock('../../api/tweetDetails', () => ({
    fetchTweetDetails: vi.fn(),
}));

vi.mock('../../api/replies', () => ({
    fetchReplies: vi.fn(),
    createReply: vi.fn(),
    updateReply: vi.fn(),
    deleteReply: vi.fn(),
}));

vi.mock('../../contexts/AuthContext', () => ({
    useAuth: () => ({
        user: {
            id: 'user-1',
            username: 'peter_g',
            email: 'peter@example.com',
        },
    }),
}));

vi.mock('../../api/likes', () => ({
    likeTweet: vi.fn(),
    unlikeTweet: vi.fn(),
}));

vi.mock('../../api/savedTweets', () => ({
    saveTweet: vi.fn(),
    unsaveTweet: vi.fn(),
}));

vi.mock('../../utils/viewReporter', () => ({
    viewReporter: {
        hasReported: vi.fn(),
        record: vi.fn(),
        flush: vi.fn(),
    },
}));

const TWEET_ID = '6f1c2a3e-0000-4000-8000-000000000001';

const DWELL_MS = 1000;

const POST = {
    id: TWEET_ID,
    views: 3,
    savedByMe: false,
    likes: 0,
    likedByMe: false,
    replyCount: 2,
    content: 'the post under discussion',
    createdAt: '2026-10-07T10:00:00.000Z',
    updatedAt: '2026-10-07T10:00:00.000Z',
    author: {
        id: 'author-1',
        username: 'ana',
        profilePictureUrl: null,
    },
    images: [],
};

const REPLY = {
    id: 'reply-1',
    tweetId: TWEET_ID,
    content: 'the first reply',
    edited: false,
    createdAt: '2026-10-07T10:05:00.000Z',
    updatedAt: '2026-10-07T10:05:00.000Z',
    author: {
        id: 'author-2',
        username: 'bob',
        profilePictureUrl: null,
    },
};

const SECOND_REPLY = {
    ...REPLY,
    id: 'reply-2',
    content: 'the second reply',
};

const MY_REPLY = {
    ...REPLY,
    id: 'reply-mine',
    content: 'my own reply',
    author: {
        id: 'user-1',
        username: 'peter_g',
        profilePictureUrl: null,
    },
};

const NO_REPLIES = {
    items: [],
    nextCursor: null,
};

const FEED_TEXT = 'the feed';

function renderPage(entries: string[]) {
    return render(
        <MemoryRouter initialEntries={entries} initialIndex={entries.length - 1}>
            <Routes>
                <Route path={ROUTES.feed} element={<p>{FEED_TEXT}</p>} />
                <Route path={ROUTES.tweet} element={<TweetDetailsPage tweetId={TWEET_ID} />} />
            </Routes>
        </MemoryRouter>,
    );
}

async function advance(milliseconds: number) {
    await act(async () => {
        await vi.advanceTimersByTimeAsync(milliseconds);
    });
}

async function press(name: string, role: 'link' | 'button') {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    await user.click(screen.getByRole(role, { name }));
}

beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
    vi.mocked(fetchTweetDetails)
        .mockReset()
        .mockResolvedValue(POST);
    vi.mocked(fetchReplies)
        .mockReset()
        .mockResolvedValue(NO_REPLIES);
    vi.mocked(createReply).mockReset();
    vi.mocked(updateReply).mockReset();
    vi.mocked(deleteReply)
        .mockReset()
        .mockResolvedValue(undefined);
    vi.mocked(viewReporter.hasReported)
        .mockReset()
        .mockReturnValue(false);
    vi.mocked(viewReporter.record).mockReset();
});

afterEach(() => {
    vi.useRealTimers();
});

function shownReplyCount(): string {
    return screen.getByText('Replies').closest('[data-action="reply"]')?.textContent ?? '';
}

describe('loading the page', () => {
    it('should_show_the_post_without_making_it_clickable_when_the_details_read_succeeds', async () => {
        renderPage([`/tweets/${TWEET_ID}`]);
        await advance(1);

        expect(screen.getByText('the post under discussion')).toBeTruthy();
        expect(screen.getByRole('article').getAttribute('data-clickable')).toBe('false');
        expect(fetchTweetDetails).toHaveBeenCalledExactlyOnceWith(TWEET_ID);
    });

    it('should_scroll_to_the_top_when_the_page_opens', async () => {
        renderPage([`/tweets/${TWEET_ID}`]);
        await advance(1);

        expect(window.scrollTo).toHaveBeenCalledWith({ top: 0 });
    });

    it('should_start_the_details_and_the_replies_reads_together_when_opened', () => {
        renderPage([`/tweets/${TWEET_ID}`]);

        expect(fetchTweetDetails).toHaveBeenCalledExactlyOnceWith(TWEET_ID);
        expect(fetchReplies).toHaveBeenCalledExactlyOnceWith(
            TWEET_ID,
            {
                cursor: null,
                size: 20,
            },
        );
    });

    it('should_show_the_post_and_the_first_replies_when_both_reads_succeed', async () => {
        vi.mocked(fetchReplies).mockResolvedValue({
            items: [REPLY, SECOND_REPLY],
            nextCursor: null,
        });
        renderPage([`/tweets/${TWEET_ID}`]);
        await advance(1);

        const replies = screen
            .getAllByRole('article', { name: /^Reply by/ })
            .map((reply) => reply.textContent);

        expect(screen.getByText('the post under discussion')).toBeTruthy();
        expect(screen.getByRole('button', { name: 'Write a reply…' })).toBeTruthy();
        expect(replies[0]).toContain('the first reply');
        expect(replies[1]).toContain('the second reply');
    });
});

describe('a missing post', () => {
    it('should_show_post_not_found_and_only_the_back_link_when_the_details_read_answers_404', async () => {
        vi.mocked(fetchTweetDetails).mockRejectedValue(new ApiError(404, []));
        renderPage([`/tweets/${TWEET_ID}`]);
        await advance(1);

        expect(screen.getByText('POST NOT FOUND')).toBeTruthy();
        expect(screen.getByRole('link', { name: 'BACK' })).toBeTruthy();
        expect(screen.queryByRole('article')).toBeNull();
        expect(screen.queryByRole('button')).toBeNull();
        expect(screen.queryByRole('button', { name: 'Write a reply…' })).toBeNull();
    });

    it('should_offer_try_again_and_show_the_post_when_the_retry_succeeds', async () => {
        vi.mocked(fetchTweetDetails).mockRejectedValueOnce(new ApiError(500, []));
        renderPage([`/tweets/${TWEET_ID}`]);
        await advance(1);
        expect(screen.queryByRole('article')).toBeNull();

        await press('TRY AGAIN', 'button');
        await advance(1);

        expect(screen.getByText('the post under discussion')).toBeTruthy();
    });
});

describe('the back link', () => {
    it('should_go_back_in_the_history_when_there_is_an_entry_behind', async () => {
        renderPage([ROUTES.feed, `/tweets/${TWEET_ID}`]);
        await advance(1);

        await press('BACK', 'link');

        expect(screen.getByText(FEED_TEXT)).toBeTruthy();
    });

    it('should_open_the_feed_when_the_page_is_the_first_entry', async () => {
        renderPage([`/tweets/${TWEET_ID}`]);
        await advance(1);

        await press('BACK', 'link');

        expect(screen.getByText(FEED_TEXT)).toBeTruthy();
    });
});

describe('the replies list', () => {
    it('should_show_the_error_with_retry_under_the_composer_when_the_replies_read_fails', async () => {
        vi.mocked(fetchReplies).mockRejectedValueOnce(new ApiError(500, []));
        renderPage([`/tweets/${TWEET_ID}`]);
        await advance(1);

        expect(screen.getByText('the post under discussion')).toBeTruthy();
        expect(screen.getByRole('button', { name: 'Write a reply…' })).toBeTruthy();
        expect(screen.getByText('SIGNAL LOST')).toBeTruthy();

        vi.mocked(fetchReplies).mockResolvedValue({
            items: [REPLY],
            nextCursor: null,
        });
        await press('TRY AGAIN', 'button');
        await advance(1);

        expect(screen.getByText('the first reply')).toBeTruthy();
    });

    it('should_show_end_of_replies_when_the_last_page_is_loaded', async () => {
        vi.mocked(fetchReplies).mockResolvedValue({
            items: [REPLY],
            nextCursor: null,
        });
        renderPage([`/tweets/${TWEET_ID}`]);
        await advance(1);

        expect(screen.getByText('END OF REPLIES')).toBeTruthy();
    });

    it('should_show_no_replies_yet_when_the_post_has_none', async () => {
        renderPage([`/tweets/${TWEET_ID}`]);
        await advance(1);

        expect(screen.getByText('NO REPLIES YET')).toBeTruthy();
    });
});

describe('sending a reply', () => {
    it('should_show_the_sent_reply_first_and_raise_the_count_by_one_when_a_reply_is_sent', async () => {
        vi.mocked(fetchReplies).mockResolvedValue({
            items: [REPLY],
            nextCursor: null,
        });
        vi.mocked(createReply).mockResolvedValue(SECOND_REPLY);
        renderPage([`/tweets/${TWEET_ID}`]);
        await advance(1);
        const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
        expect(shownReplyCount()).toContain('2');

        await user.click(screen.getByRole('button', { name: 'Write a reply…' }));
        await user.paste('the second reply');
        await user.click(screen.getByRole('button', { name: 'REPLY' }));
        await advance(1);

        const replies = screen
            .getAllByRole('article', { name: /^Reply by/ })
            .map((reply) => reply.textContent);

        expect(replies[0]).toContain('the second reply');
        expect(replies[1]).toContain('the first reply');
        expect(shownReplyCount()).toContain('3');
    });

    it('should_show_a_reply_once_when_the_server_delivers_it_after_it_was_sent', async () => {
        vi.mocked(fetchReplies).mockResolvedValue({
            items: [REPLY],
            nextCursor: null,
        });
        vi.mocked(createReply).mockResolvedValue(REPLY);
        renderPage([`/tweets/${TWEET_ID}`]);
        await advance(1);
        const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

        await user.click(screen.getByRole('button', { name: 'Write a reply…' }));
        await user.paste('the first reply');
        await user.click(screen.getByRole('button', { name: 'REPLY' }));
        await advance(1);

        expect(screen.getAllByRole('article', { name: /^Reply by/ })).toHaveLength(1);
    });
});

describe('your own reply', () => {
    it('should_show_edited_and_the_new_text_when_an_edit_is_saved', async () => {
        vi.mocked(fetchReplies).mockResolvedValue({
            items: [MY_REPLY],
            nextCursor: null,
        });
        vi.mocked(updateReply).mockResolvedValue({
            ...MY_REPLY,
            content: 'my changed reply',
            edited: true,
        });
        renderPage([`/tweets/${TWEET_ID}`]);
        await advance(1);
        const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

        await user.click(screen.getByRole('button', { name: 'EDIT' }));
        await user.clear(screen.getByLabelText('EDIT YOUR REPLY'));
        await user.paste('my changed reply');
        await user.click(screen.getByRole('button', { name: 'SAVE' }));
        await advance(1);

        expect(screen.getByText('my changed reply')).toBeTruthy();
        expect(screen.getByText('EDITED')).toBeTruthy();
    });

    it('should_remove_the_reply_and_lower_the_count_by_one_when_it_is_deleted', async () => {
        vi.mocked(fetchReplies).mockResolvedValue({
            items: [MY_REPLY],
            nextCursor: null,
        });
        renderPage([`/tweets/${TWEET_ID}`]);
        await advance(1);
        const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

        await user.click(screen.getByRole('button', { name: 'DELETE' }));
        await user.click(screen.getByRole('button', { name: 'YES' }));
        await advance(1);

        expect(screen.queryByText('my own reply')).toBeNull();
        expect(shownReplyCount()).toContain('1');
        expect(screen.getByText('NO REPLIES YET')).toBeTruthy();
    });
});

describe('views', () => {
    it('should_report_the_post_as_viewed_once_when_it_is_shown', async () => {
        renderPage([`/tweets/${TWEET_ID}`]);
        await advance(1);
        const post = screen.getByRole('article').parentElement;

        if (!post) {
            throw new Error('Expected the post to sit in its tracking wrapper.');
        }

        await intersect(post, 1);
        await advance(DWELL_MS);

        expect(viewReporter.record).toHaveBeenCalledExactlyOnceWith(TWEET_ID);
    });
});
