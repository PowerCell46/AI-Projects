import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../api/http';
import { createReply } from '../../../api/replies';
import ReplyComposer from './ReplyComposer';


vi.mock('../../../api/replies', () => ({
    createReply: vi.fn(),
}));

const TWEET_ID = '6f1c2a3e-0000-4000-8000-000000000001';

const REPLY = {
    id: '6f1c2a3e-0000-4000-8000-000000000002',
    tweetId: TWEET_ID,
    content: 'hello world',
    edited: false,
    createdAt: '2026-10-07T10:00:00.000Z',
    updatedAt: '2026-10-07T10:00:00.000Z',
    author: {
        id: 'user-1',
        username: 'ana',
        profilePictureUrl: null,
    },
};

const MAX_CHARACTERS = 280;

const onSent = vi.fn();

function field(): HTMLTextAreaElement {
    return screen.getByLabelText<HTMLTextAreaElement>('YOUR REPLY');
}

function replyButton(): HTMLElement {
    return screen.getByRole('button', { name: 'REPLY' });
}

// jsdom plays no animation, so the end of the folding is sent by hand.
function finishClosing() {
    const form = field().closest('form');

    if (form) {
        fireEvent.animationEnd(form);
    }
}

async function typeAndSend(text: string) {
    await userEvent.click(field());
    await userEvent.paste(text);
    await userEvent.click(replyButton());
}

beforeEach(async () => {
    onSent.mockReset();
    vi.mocked(createReply)
        .mockReset()
        .mockResolvedValue(REPLY);
    render(<ReplyComposer tweetId={TWEET_ID} onSent={onSent} />);
    await userEvent.click(screen.getByRole('button', { name: 'Write a reply…' }));
});

describe('collapsing', () => {
    it('should_fold_back_into_its_row_when_the_empty_field_loses_focus', async () => {
        await userEvent.tab({ shift: true });

        expect(field().closest('form')?.getAttribute('data-closing')).toBe('true');

        finishClosing();

        expect(screen.queryByLabelText('YOUR REPLY')).toBeNull();
        expect(screen.getByRole('button', { name: 'Write a reply…' })).toBeTruthy();
    });

    it('should_stay_open_when_the_field_with_text_loses_focus', async () => {
        await userEvent.paste('half a thought');
        await userEvent.tab();

        expect(field().value).toBe('half a thought');
    });

    it('should_clear_the_text_and_fold_when_cancel_is_pressed', async () => {
        await userEvent.paste('half a thought');
        await userEvent.click(screen.getByRole('button', { name: 'CANCEL' }));
        finishClosing();

        expect(screen.queryByLabelText('YOUR REPLY')).toBeNull();

        await userEvent.click(screen.getByRole('button', { name: 'Write a reply…' }));

        expect(field().value).toBe('');
    });

    it('should_fold_when_a_reply_is_sent', async () => {
        await typeAndSend('hello world');

        expect(field().closest('form')?.getAttribute('data-closing')).toBe('true');

        finishClosing();

        expect(screen.queryByLabelText('YOUR REPLY')).toBeNull();
    });
});

describe('the counter', () => {
    it('should_count_code_points_when_the_text_has_emoji', async () => {
        await userEvent.click(field());

        await userEvent.paste('😀😀');

        expect(screen.getByText(`2 / ${MAX_CHARACTERS}`)).toBeTruthy();
    });
});

describe('the reply button', () => {
    it('should_be_off_when_the_text_is_empty', () => {
        expect(replyButton().getAttribute('aria-disabled')).toBe('true');
    });

    it('should_be_off_when_the_text_is_blank', async () => {
        await userEvent.click(field());
        await userEvent.paste('   ');

        expect(replyButton().getAttribute('aria-disabled')).toBe('true');
    });

    it('should_be_off_when_the_text_is_281_code_points', async () => {
        await userEvent.click(field());
        await userEvent.paste('a'.repeat(MAX_CHARACTERS + 1));

        expect(replyButton().getAttribute('aria-disabled')).toBe('true');
    });

    it('should_be_on_when_the_text_is_exactly_280_code_points', async () => {
        await userEvent.click(field());
        await userEvent.paste('a'.repeat(MAX_CHARACTERS));

        expect(replyButton().getAttribute('aria-disabled')).toBe('false');
    });

    it('should_send_nothing_when_pressed_while_off', async () => {
        await userEvent.click(replyButton());

        expect(createReply).not.toHaveBeenCalled();
    });
});

describe('sending', () => {
    it('should_send_the_trimmed_text_and_hand_the_reply_on_when_sent', async () => {
        await typeAndSend('  hello world  ');

        expect(createReply).toHaveBeenCalledExactlyOnceWith(TWEET_ID, 'hello world');
        expect(onSent).toHaveBeenCalledExactlyOnceWith(REPLY);
    });

    it('should_clear_the_field_when_the_reply_is_sent', async () => {
        await typeAndSend('hello world');
        finishClosing();
        await userEvent.click(screen.getByRole('button', { name: 'Write a reply…' }));

        expect(field().value).toBe('');
    });

    it('should_show_this_post_was_deleted_under_the_field_when_the_server_answers_404', async () => {
        vi.mocked(createReply).mockRejectedValue(new ApiError(404, []));

        await typeAndSend('hello world');

        expect(screen.getByRole('alert').textContent).toBe('This post was deleted.');
        expect(field().value).toBe('hello world');
        expect(onSent).not.toHaveBeenCalled();
    });

    it('should_show_busy_try_again_under_the_field_when_the_server_answers_503', async () => {
        vi.mocked(createReply).mockRejectedValue(new ApiError(503, []));

        await typeAndSend('hello world');

        expect(screen.getByRole('alert').textContent).toBe('Busy, try again.');
    });

    it('should_show_couldnt_send_under_the_field_when_the_server_answers_another_error', async () => {
        vi.mocked(createReply).mockRejectedValue(new ApiError(500, []));

        await typeAndSend('hello world');

        expect(screen.getByRole('alert').textContent).toBe("Couldn't send. Try again.");
    });

    it('should_clear_the_error_when_the_next_send_goes_through', async () => {
        vi.mocked(createReply).mockRejectedValueOnce(new ApiError(503, []));
        await typeAndSend('hello world');

        await userEvent.click(replyButton());

        expect(screen.getByRole('alert').textContent).toBe('');
        expect(onSent).toHaveBeenCalledExactlyOnceWith(REPLY);
    });
});
