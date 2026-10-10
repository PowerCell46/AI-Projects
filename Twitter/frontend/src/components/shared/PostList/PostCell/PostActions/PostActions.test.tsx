import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ComponentProps } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { likeTweet, unlikeTweet } from '../../../../../api/likes';
import { saveTweet, unsaveTweet } from '../../../../../api/savedTweets';
import PostActions from './PostActions';


vi.mock('../../../../../api/likes', () => ({
    likeTweet: vi.fn(),
    unlikeTweet: vi.fn(),
}));

vi.mock('../../../../../api/savedTweets', () => ({
    saveTweet: vi.fn(),
    unsaveTweet: vi.fn(),
}));

const TWEET_ID = '6f1c2a3e-0000-4000-8000-000000000001';

beforeEach(() => {
    vi.mocked(likeTweet)
        .mockReset()
        .mockResolvedValue(undefined);
    vi.mocked(unlikeTweet)
        .mockReset()
        .mockResolvedValue(undefined);
    vi.mocked(saveTweet)
        .mockReset()
        .mockResolvedValue(undefined);
    vi.mocked(unsaveTweet)
        .mockReset()
        .mockResolvedValue(undefined);
});

function renderActions(props: Partial<ComponentProps<typeof PostActions>> = {}) {
    return render(
        <MemoryRouter>
            <PostActions
                tweetId={TWEET_ID}
                isSavedInitially={false}
                isLikedInitially={false}
                likeCount={0}
                replyCount={0}
                isReplyLinked
                {...props}
            />
        </MemoryRouter>,
    );
}

function likeButton(): HTMLElement {
    return screen.getByRole('button', { name: /^like/i });
}

function saveButton(): HTMLElement {
    return screen.getByRole('button', { name: /^save/i });
}

describe('like', () => {
    it('should_press_the_button_and_move_the_count_to_one_at_once_when_clicked', async () => {
        renderActions();

        await userEvent.click(likeButton());

        expect(likeButton().getAttribute('aria-pressed')).toBe('true');
        expect(likeButton().textContent).toContain('1');
        expect(likeTweet).toHaveBeenCalledExactlyOnceWith(TWEET_ID);
    });

    it('should_unpress_the_button_and_move_the_count_back_to_zero_when_clicked_again', async () => {
        renderActions();
        await userEvent.click(likeButton());

        await userEvent.click(likeButton());

        expect(likeButton().getAttribute('aria-pressed')).toBe('false');
        expect(likeButton().textContent).toContain('0');
        expect(unlikeTweet).toHaveBeenCalledExactlyOnceWith(TWEET_ID);
    });

    it('should_put_the_like_back_without_a_message_when_the_request_fails', async () => {
        vi.mocked(likeTweet).mockRejectedValue(new Error('The request failed.'));
        renderActions();

        await userEvent.click(likeButton());

        expect(likeButton().getAttribute('aria-pressed')).toBe('false');
        expect(likeButton().textContent).toContain('0');
        expect(screen.queryByRole('alert')).toBeNull();
    });
});

describe('save', () => {
    it('should_start_unpressed_when_the_post_is_not_saved', () => {
        renderActions();

        expect(saveButton().getAttribute('aria-pressed')).toBe('false');
    });

    it('should_start_pressed_when_the_post_is_already_saved', () => {
        renderActions({ isSavedInitially: true });

        expect(saveButton().getAttribute('aria-pressed')).toBe('true');
    });

    it('should_press_the_button_and_save_the_tweet_when_clicked', async () => {
        renderActions();

        await userEvent.click(saveButton());

        expect(saveButton().getAttribute('aria-pressed')).toBe('true');
        expect(saveTweet).toHaveBeenCalledExactlyOnceWith(TWEET_ID);
    });

    it('should_unpress_the_button_and_unsave_the_tweet_when_a_saved_post_is_clicked', async () => {
        renderActions({ isSavedInitially: true });

        await userEvent.click(saveButton());

        expect(saveButton().getAttribute('aria-pressed')).toBe('false');
        expect(unsaveTweet).toHaveBeenCalledExactlyOnceWith(TWEET_ID);
    });

    it('should_put_the_bookmark_back_when_the_request_fails', async () => {
        vi.mocked(saveTweet).mockRejectedValue(new Error('The request failed.'));
        renderActions();

        await userEvent.click(saveButton());

        expect(saveButton().getAttribute('aria-pressed')).toBe('false');
    });

    it('should_show_no_count_next_to_the_bookmark', () => {
        renderActions({ isSavedInitially: true });

        expect(saveButton().textContent).not.toMatch(/\d/);
    });
});

describe('reporting to the caller', () => {
    it('should_report_the_like_and_then_the_old_value_when_the_like_request_fails', async () => {
        const onChange = vi.fn();
        vi.mocked(likeTweet).mockRejectedValue(new Error('The request failed.'));
        renderActions({
            likeCount: 3,
            onChange,
        });

        await userEvent.click(likeButton());

        expect(onChange.mock.calls).toEqual([
            [{ likedByMe: true, likes: 4 }],
            [{ likedByMe: false, likes: 3 }],
        ]);
    });

    it('should_report_the_unlike_and_then_the_old_value_when_the_unlike_request_fails', async () => {
        const onChange = vi.fn();
        vi.mocked(unlikeTweet).mockRejectedValue(new Error('The request failed.'));
        renderActions({
            likeCount: 3,
            isLikedInitially: true,
            onChange,
        });

        await userEvent.click(likeButton());

        expect(onChange.mock.calls).toEqual([
            [{ likedByMe: false, likes: 2 }],
            [{ likedByMe: true, likes: 3 }],
        ]);
    });

    it('should_report_the_save_and_then_the_old_value_when_the_save_request_fails', async () => {
        const onChange = vi.fn();
        vi.mocked(saveTweet).mockRejectedValue(new Error('The request failed.'));
        renderActions({ onChange });

        await userEvent.click(saveButton());

        expect(onChange.mock.calls).toEqual([
            [{ savedByMe: true }],
            [{ savedByMe: false }],
        ]);
    });

    it('should_report_only_the_click_when_the_request_succeeds', async () => {
        const onChange = vi.fn();
        renderActions({ onChange });

        await userEvent.click(saveButton());

        expect(onChange.mock.calls).toEqual([[{ savedByMe: true }]]);
    });
});

describe('last click wins', () => {
    it('should_send_the_unsave_after_the_save_when_the_bookmark_is_clicked_twice_while_saving', async () => {
        let finishSaving = () => {};
        vi.mocked(saveTweet).mockReturnValue(new Promise<void>((resolve) => {
            finishSaving = resolve;
        }));
        renderActions();
        await userEvent.click(saveButton());
        await userEvent.click(saveButton());

        expect(unsaveTweet).not.toHaveBeenCalled();

        await act(async () => {
            finishSaving();
        });

        expect(unsaveTweet).toHaveBeenCalledExactlyOnceWith(TWEET_ID);
        expect(saveButton().getAttribute('aria-pressed')).toBe('false');
    });

    it('should_not_hold_the_save_back_when_a_like_is_in_flight', async () => {
        vi.mocked(likeTweet).mockReturnValue(new Promise<void>(() => {}));
        renderActions();
        await userEvent.click(likeButton());

        await userEvent.click(saveButton());

        expect(saveTweet).toHaveBeenCalledExactlyOnceWith(TWEET_ID);
    });
});

describe('like from the server state', () => {
    it('should_show_3_and_unpressed_when_the_post_has_3_likes_and_is_not_liked_by_me', () => {
        renderActions({ likeCount: 3 });

        expect(likeButton().getAttribute('aria-pressed')).toBe('false');
        expect(likeButton().textContent).toContain('3');
    });

    it('should_show_3_and_pressed_when_the_post_has_3_likes_and_is_liked_by_me', () => {
        renderActions({
            likeCount: 3,
            isLikedInitially: true,
        });

        expect(likeButton().getAttribute('aria-pressed')).toBe('true');
        expect(likeButton().textContent).toContain('3');
    });

    it('should_show_4_and_pressed_when_a_post_with_3_likes_is_liked', async () => {
        renderActions({ likeCount: 3 });

        await userEvent.click(likeButton());

        expect(likeButton().getAttribute('aria-pressed')).toBe('true');
        expect(likeButton().textContent).toContain('4');
    });

    it('should_show_2_when_a_liked_post_with_3_likes_is_unliked', async () => {
        renderActions({
            likeCount: 3,
            isLikedInitially: true,
        });

        await userEvent.click(likeButton());

        expect(likeButton().getAttribute('aria-pressed')).toBe('false');
        expect(likeButton().textContent).toContain('2');
        expect(unlikeTweet).toHaveBeenCalledExactlyOnceWith(TWEET_ID);
    });

    it('should_show_3_when_a_post_with_3_likes_is_liked_and_unliked', async () => {
        renderActions({ likeCount: 3 });

        await userEvent.click(likeButton());
        await userEvent.click(likeButton());

        expect(likeButton().textContent).toContain('3');
    });

    it('should_show_3_unpressed_and_no_alert_when_the_like_request_fails', async () => {
        vi.mocked(likeTweet).mockRejectedValue(new Error('The request failed.'));
        renderActions({ likeCount: 3 });

        await userEvent.click(likeButton());

        expect(likeButton().getAttribute('aria-pressed')).toBe('false');
        expect(likeButton().textContent).toContain('3');
        expect(screen.queryByRole('alert')).toBeNull();
    });

    it('should_show_3_and_pressed_when_the_unlike_of_a_liked_post_fails', async () => {
        vi.mocked(unlikeTweet).mockRejectedValue(new Error('The request failed.'));
        renderActions({
            likeCount: 3,
            isLikedInitially: true,
        });

        await userEvent.click(likeButton());

        expect(likeButton().getAttribute('aria-pressed')).toBe('true');
        expect(likeButton().textContent).toContain('3');
    });

    it('should_show_1.2K_for_1299_likes_and_1.3K_when_it_is_liked', async () => {
        renderActions({ likeCount: 1_299 });

        expect(likeButton().textContent).toContain('1.2K');

        await userEvent.click(likeButton());

        expect(likeButton().textContent).toContain('1.3K');
    });

    it('should_show_0_and_pressed_then_0_unpressed_never_minus_1_when_a_post_has_0_likes_and_is_liked_by_me', async () => {
        renderActions({
            likeCount: 0,
            isLikedInitially: true,
        });

        expect(likeButton().getAttribute('aria-pressed')).toBe('true');
        expect(likeButton().textContent).toContain('0');

        await userEvent.click(likeButton());

        expect(likeButton().getAttribute('aria-pressed')).toBe('false');
        expect(likeButton().textContent).toBe('Like0');
    });
});

describe('the reply link', () => {
    it('should_show_the_formatted_reply_count_when_the_post_has_replies', () => {
        renderActions({ replyCount: 1299 });

        expect(screen.getByRole('link', { name: /^replies/i }).textContent).toContain('1.2K');
    });

    it('should_point_at_the_tweet_details_route_when_rendered', () => {
        renderActions();

        expect(screen.getByRole('link', { name: /^replies/i }).getAttribute('href')).toBe(`/tweets/${TWEET_ID}`);
    });
});

describe('the reply count', () => {
    it('should_be_a_link_to_the_post_when_the_reply_is_linked', () => {
        renderActions({ replyCount: 3 });

        expect(screen.getByRole('link', { name: /^replies/i }).textContent).toContain('3');
    });

    it('should_be_plain_text_when_the_reply_is_not_linked', () => {
        renderActions({ replyCount: 3, isReplyLinked: false });

        expect(screen.queryByRole('link')).toBeNull();
        expect(screen.getByText('Replies').closest('[data-action="reply"]')?.textContent).toContain('3');
    });
});
