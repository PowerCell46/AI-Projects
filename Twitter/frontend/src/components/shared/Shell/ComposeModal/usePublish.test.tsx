import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../../api/http';
import { publishTweet } from '../../../../api/tweets';
import type { PublishedTweet } from '../../../../api/tweets';
import { SIGNAL_LOST_MESSAGE } from '../../../../utils/authErrors';
import { usePublish } from './usePublish';


vi.mock('../../../../api/tweets', () => ({
    publishTweet: vi.fn(),
}));

const REQUEST = {
    content: 'hello',
    images: [],
};

const PUBLISHED: PublishedTweet = {
    id: 'tweet-1',
    authorId: 'user-1',
    content: 'hello',
    createdAt: '2026-10-04T12:00:00Z',
    updatedAt: '2026-10-04T12:00:00Z',
    images: [],
};

const onPublished = vi.fn();

beforeEach(() => {
    vi.mocked(publishTweet).mockReset();
    onPublished.mockReset();
});

describe('usePublish', () => {
    it('should_send_the_request_and_hand_the_published_tweet_on', async () => {
        vi.mocked(publishTweet).mockResolvedValue(PUBLISHED);
        const { result } = renderHook(() => usePublish(onPublished));

        await act(async () => {
            result.current.publish(REQUEST);
        });

        expect(publishTweet).toHaveBeenCalledExactlyOnceWith(REQUEST);
        expect(onPublished).toHaveBeenCalledExactlyOnceWith(PUBLISHED);
    });

    it('should_report_publishing_while_the_request_is_on_its_way', async () => {
        vi.mocked(publishTweet).mockReturnValue(new Promise<PublishedTweet>(() => undefined));
        const { result } = renderHook(() => usePublish(onPublished));

        await act(async () => {
            result.current.publish(REQUEST);
        });

        expect(result.current.isPublishing).toBe(true);
    });

    it('should_send_one_request_when_published_twice_while_the_first_is_on_its_way', async () => {
        vi.mocked(publishTweet).mockReturnValue(new Promise<PublishedTweet>(() => undefined));
        const { result } = renderHook(() => usePublish(onPublished));

        await act(async () => {
            result.current.publish(REQUEST);
        });
        await act(async () => {
            result.current.publish(REQUEST);
        });

        expect(publishTweet).toHaveBeenCalledTimes(1);
    });

    it('should_show_the_servers_words_and_allow_another_try_when_the_post_is_refused', async () => {
        vi.mocked(publishTweet).mockRejectedValue(new ApiError(400, ['A tweet can have at most 4 images.']));
        const { result } = renderHook(() => usePublish(onPublished));

        await act(async () => {
            result.current.publish(REQUEST);
        });

        expect(result.current.errorMessage).toBe('A TWEET CAN HAVE AT MOST 4 IMAGES');
        expect(result.current.isPublishing).toBe(false);
        expect(onPublished).not.toHaveBeenCalled();
    });

    it('should_say_signal_lost_when_the_network_fails', async () => {
        vi.mocked(publishTweet).mockRejectedValue(new ApiError(0, []));
        const { result } = renderHook(() => usePublish(onPublished));

        await act(async () => {
            result.current.publish(REQUEST);
        });

        expect(result.current.errorMessage).toBe(SIGNAL_LOST_MESSAGE);
    });

    it('should_send_again_when_published_after_a_failure', async () => {
        vi.mocked(publishTweet)
            .mockRejectedValueOnce(new ApiError(500, []))
            .mockResolvedValueOnce(PUBLISHED);
        const { result } = renderHook(() => usePublish(onPublished));
        await act(async () => {
            result.current.publish(REQUEST);
        });

        await act(async () => {
            result.current.publish(REQUEST);
        });

        expect(publishTweet).toHaveBeenCalledTimes(2);
        expect(onPublished).toHaveBeenCalledExactlyOnceWith(PUBLISHED);
    });

    it('should_clear_the_error_when_a_new_attempt_starts', async () => {
        vi.mocked(publishTweet)
            .mockRejectedValueOnce(new ApiError(500, []))
            .mockReturnValueOnce(new Promise<PublishedTweet>(() => undefined));
        const { result } = renderHook(() => usePublish(onPublished));
        await act(async () => {
            result.current.publish(REQUEST);
        });

        await act(async () => {
            result.current.publish(REQUEST);
        });

        expect(result.current.errorMessage).toBe('');
    });
});
