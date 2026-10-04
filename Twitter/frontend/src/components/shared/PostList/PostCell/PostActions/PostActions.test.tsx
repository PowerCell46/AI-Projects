import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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

function likeButton(): HTMLElement {
    return screen.getByRole('button', { name: /^like/i });
}

function saveButton(): HTMLElement {
    return screen.getByRole('button', { name: /^save/i });
}

describe('like', () => {
    it('should_show_a_zero_count_and_an_unpressed_button_when_the_post_loads', () => {
        render(<PostActions tweetId={TWEET_ID} isSavedInitially={false} />);

        expect(likeButton().getAttribute('aria-pressed')).toBe('false');
        expect(likeButton().textContent).toContain('0');
    });

    it('should_press_the_button_and_move_the_count_to_one_at_once_when_clicked', async () => {
        render(<PostActions tweetId={TWEET_ID} isSavedInitially={false} />);

        await userEvent.click(likeButton());

        expect(likeButton().getAttribute('aria-pressed')).toBe('true');
        expect(likeButton().textContent).toContain('1');
        expect(likeTweet).toHaveBeenCalledExactlyOnceWith(TWEET_ID);
    });

    it('should_unpress_the_button_and_move_the_count_back_to_zero_when_clicked_again', async () => {
        render(<PostActions tweetId={TWEET_ID} isSavedInitially={false} />);
        await userEvent.click(likeButton());

        await userEvent.click(likeButton());

        expect(likeButton().getAttribute('aria-pressed')).toBe('false');
        expect(likeButton().textContent).toContain('0');
        expect(unlikeTweet).toHaveBeenCalledExactlyOnceWith(TWEET_ID);
    });

    it('should_put_the_like_back_without_a_message_when_the_request_fails', async () => {
        vi.mocked(likeTweet).mockRejectedValue(new Error('The request failed.'));
        render(<PostActions tweetId={TWEET_ID} isSavedInitially={false} />);

        await userEvent.click(likeButton());

        expect(likeButton().getAttribute('aria-pressed')).toBe('false');
        expect(likeButton().textContent).toContain('0');
        expect(screen.queryByRole('alert')).toBeNull();
    });
});

describe('save', () => {
    it('should_start_unpressed_when_the_post_is_not_saved', () => {
        render(<PostActions tweetId={TWEET_ID} isSavedInitially={false} />);

        expect(saveButton().getAttribute('aria-pressed')).toBe('false');
    });

    it('should_start_pressed_when_the_post_is_already_saved', () => {
        render(<PostActions tweetId={TWEET_ID} isSavedInitially />);

        expect(saveButton().getAttribute('aria-pressed')).toBe('true');
    });

    it('should_press_the_button_and_save_the_tweet_when_clicked', async () => {
        render(<PostActions tweetId={TWEET_ID} isSavedInitially={false} />);

        await userEvent.click(saveButton());

        expect(saveButton().getAttribute('aria-pressed')).toBe('true');
        expect(saveTweet).toHaveBeenCalledExactlyOnceWith(TWEET_ID);
    });

    it('should_unpress_the_button_and_unsave_the_tweet_when_a_saved_post_is_clicked', async () => {
        render(<PostActions tweetId={TWEET_ID} isSavedInitially />);

        await userEvent.click(saveButton());

        expect(saveButton().getAttribute('aria-pressed')).toBe('false');
        expect(unsaveTweet).toHaveBeenCalledExactlyOnceWith(TWEET_ID);
    });

    it('should_put_the_bookmark_back_when_the_request_fails', async () => {
        vi.mocked(saveTweet).mockRejectedValue(new Error('The request failed.'));
        render(<PostActions tweetId={TWEET_ID} isSavedInitially={false} />);

        await userEvent.click(saveButton());

        expect(saveButton().getAttribute('aria-pressed')).toBe('false');
    });

    it('should_show_no_count_next_to_the_bookmark', () => {
        render(<PostActions tweetId={TWEET_ID} isSavedInitially />);

        expect(saveButton().textContent).not.toMatch(/\d/);
    });
});

describe('last click wins', () => {
    it('should_send_the_unsave_after_the_save_when_the_bookmark_is_clicked_twice_while_saving', async () => {
        let finishSaving = () => {};
        vi.mocked(saveTweet).mockReturnValue(new Promise<void>((resolve) => {
            finishSaving = resolve;
        }));
        render(<PostActions tweetId={TWEET_ID} isSavedInitially={false} />);
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
        render(<PostActions tweetId={TWEET_ID} isSavedInitially={false} />);
        await userEvent.click(likeButton());

        await userEvent.click(saveButton());

        expect(saveTweet).toHaveBeenCalledExactlyOnceWith(TWEET_ID);
    });
});
