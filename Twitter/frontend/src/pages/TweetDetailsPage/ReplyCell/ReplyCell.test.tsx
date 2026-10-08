import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../api/http';
import { deleteReply, updateReply } from '../../../api/replies';
import type { Reply } from '../../../api/replies';
import ReplyCell from './ReplyCell';


vi.mock('../../../api/replies', () => ({
    updateReply: vi.fn(),
    deleteReply: vi.fn(),
}));

const TWEET_ID = '6f1c2a3e-0000-4000-8000-000000000001';

const REPLY_AUTHOR_ID = 'reply-author';

const POST_AUTHOR_ID = 'post-author';

const STRANGER_ID = 'stranger';

const NOW = new Date('2026-10-07T10:30:00.000Z');

const MAX_CHARACTERS = 280;

const REPLY: Reply = {
    id: '6f1c2a3e-0000-4000-8000-000000000002',
    tweetId: TWEET_ID,
    content: 'the reply text',
    edited: false,
    createdAt: '2026-10-07T10:00:00.000Z',
    updatedAt: '2026-10-07T10:00:00.000Z',
    author: {
        id: REPLY_AUTHOR_ID,
        username: 'ana',
        profilePictureUrl: null,
    },
};

const onChanged = vi.fn();

const onRemoved = vi.fn();

function renderCell(currentUserId: string | null = REPLY_AUTHOR_ID, changes: Partial<Reply> = {}) {
    return render(
        <ReplyCell
            reply={{ ...REPLY, ...changes }}
            position={1}
            isEntering={false}
            now={NOW}
            currentUserId={currentUserId}
            postAuthorId={POST_AUTHOR_ID}
            onChanged={onChanged}
            onRemoved={onRemoved}
        />,
    );
}

function button(name: string): HTMLElement {
    return screen.getByRole('button', { name });
}

function editField(): HTMLTextAreaElement {
    return screen.getByLabelText<HTMLTextAreaElement>('EDIT YOUR REPLY');
}

async function startEditing(text: string) {
    await userEvent.click(button('EDIT'));
    await userEvent.clear(editField());
    await userEvent.paste(text);
}

beforeEach(() => {
    onChanged.mockReset();
    onRemoved.mockReset();
    vi.mocked(updateReply)
        .mockReset()
        .mockResolvedValue({
            ...REPLY,
            content: 'the changed text',
            edited: true,
        });
    vi.mocked(deleteReply)
        .mockReset()
        .mockResolvedValue(undefined);
});

describe('the index', () => {
    it('should_show_the_position_with_two_digits_and_hide_it_from_screen_readers', () => {
        renderCell();

        expect(screen.getByText('01').getAttribute('aria-hidden')).toBe('true');
    });
});

describe('the reply', () => {
    it('should_show_edited_when_the_reply_was_edited', () => {
        renderCell(STRANGER_ID, { edited: true });

        expect(screen.getByText('EDITED')).toBeTruthy();
    });

    it('should_not_show_edited_when_the_reply_was_not_edited', () => {
        renderCell(STRANGER_ID);

        expect(screen.queryByText('EDITED')).toBeNull();
    });
});

describe('the buttons', () => {
    it('should_show_edit_only_on_your_own_reply', () => {
        renderCell(REPLY_AUTHOR_ID);

        expect(screen.queryByRole('button', { name: 'EDIT' })).not.toBeNull();
    });

    it('should_not_show_edit_when_the_reply_is_not_yours_even_when_the_post_is', () => {
        renderCell(POST_AUTHOR_ID);

        expect(screen.queryByRole('button', { name: 'EDIT' })).toBeNull();
    });

    it('should_show_delete_on_your_own_reply', () => {
        renderCell(REPLY_AUTHOR_ID);

        expect(screen.queryByRole('button', { name: 'DELETE' })).not.toBeNull();
    });

    it('should_show_delete_on_any_reply_when_the_post_is_yours', () => {
        renderCell(POST_AUTHOR_ID);

        expect(screen.queryByRole('button', { name: 'DELETE' })).not.toBeNull();
    });

    it('should_not_show_delete_on_someone_elses_reply_when_the_post_is_not_yours', () => {
        renderCell(STRANGER_ID);

        expect(screen.queryByRole('button', { name: 'DELETE' })).toBeNull();
    });

    it('should_show_no_buttons_when_nobody_is_signed_in', () => {
        renderCell(null);

        expect(screen.queryByRole('button')).toBeNull();
    });
});

describe('editing', () => {
    it('should_save_the_server_reply_and_leave_the_editor_when_save_succeeds', async () => {
        renderCell();

        await startEditing('  the changed text  ');
        await userEvent.click(button('SAVE'));

        expect(updateReply).toHaveBeenCalledExactlyOnceWith(TWEET_ID, REPLY.id, 'the changed text');
        expect(onChanged).toHaveBeenCalledExactlyOnceWith({
            ...REPLY,
            content: 'the changed text',
            edited: true,
        });
        expect(screen.queryByLabelText('EDIT YOUR REPLY')).toBeNull();
        expect(document.activeElement).toBe(button('EDIT'));
    });

    it('should_open_the_editor_on_the_current_text_with_focus_in_it_when_edit_is_pressed', async () => {
        renderCell();

        await userEvent.click(button('EDIT'));

        expect(editField().value).toBe('the reply text');
        expect(document.activeElement).toBe(editField());
    });

    it('should_restore_the_text_and_return_focus_to_edit_when_cancel_is_pressed', async () => {
        renderCell();
        await startEditing('something else');

        await userEvent.click(button('CANCEL'));

        expect(screen.getByText('the reply text')).toBeTruthy();
        expect(updateReply).not.toHaveBeenCalled();
        expect(document.activeElement).toBe(button('EDIT'));
    });

    it('should_cancel_and_return_focus_to_edit_when_escape_is_pressed', async () => {
        renderCell();
        await startEditing('something else');

        await userEvent.keyboard('{Escape}');

        expect(screen.getByText('the reply text')).toBeTruthy();
        expect(document.activeElement).toBe(button('EDIT'));
    });

    it('should_turn_save_off_when_the_text_is_unchanged', async () => {
        renderCell();

        await userEvent.click(button('EDIT'));

        expect(button('SAVE').getAttribute('aria-disabled')).toBe('true');
    });

    it('should_turn_save_off_when_the_text_is_empty', async () => {
        renderCell();

        await startEditing('   ');

        expect(button('SAVE').getAttribute('aria-disabled')).toBe('true');
    });

    it('should_turn_save_off_when_the_text_is_over_280', async () => {
        renderCell();

        await startEditing('a'.repeat(MAX_CHARACTERS + 1));

        expect(button('SAVE').getAttribute('aria-disabled')).toBe('true');
    });

    it('should_show_the_error_under_the_field_and_keep_the_text_when_save_fails', async () => {
        vi.mocked(updateReply).mockRejectedValue(new ApiError(404, []));
        renderCell();
        await startEditing('the changed text');

        await userEvent.click(button('SAVE'));

        expect(screen.getByText("Couldn't save. Try again.")).toBeTruthy();
        expect(editField().value).toBe('the changed text');
        expect(onChanged).not.toHaveBeenCalled();
    });
});

describe('deleting', () => {
    it('should_ask_delete_yes_no_with_focus_on_no_when_delete_is_pressed', async () => {
        renderCell();

        await userEvent.click(button('DELETE'));

        expect(screen.getByText('DELETE?')).toBeTruthy();
        expect(document.activeElement).toBe(button('NO'));
        expect(deleteReply).not.toHaveBeenCalled();
    });

    it('should_go_back_to_delete_with_focus_on_it_when_no_is_pressed', async () => {
        renderCell();
        await userEvent.click(button('DELETE'));

        await userEvent.click(button('NO'));

        expect(screen.queryByText('DELETE?')).toBeNull();
        expect(document.activeElement).toBe(button('DELETE'));
        expect(onRemoved).not.toHaveBeenCalled();
    });

    it('should_remove_the_reply_when_yes_is_pressed', async () => {
        renderCell();
        await userEvent.click(button('DELETE'));

        await userEvent.click(button('YES'));

        expect(deleteReply).toHaveBeenCalledExactlyOnceWith(TWEET_ID, REPLY.id);
        expect(onRemoved).toHaveBeenCalledExactlyOnceWith(REPLY.id);
    });

    it('should_remove_the_reply_when_the_server_answers_404', async () => {
        vi.mocked(deleteReply).mockRejectedValue(new ApiError(404, []));
        renderCell();
        await userEvent.click(button('DELETE'));

        await userEvent.click(button('YES'));

        expect(onRemoved).toHaveBeenCalledExactlyOnceWith(REPLY.id);
    });

    it('should_show_the_error_next_to_the_reply_when_delete_fails_otherwise', async () => {
        vi.mocked(deleteReply).mockRejectedValue(new ApiError(500, []));
        renderCell();
        await userEvent.click(button('DELETE'));

        await userEvent.click(button('YES'));

        expect(screen.getByText("Couldn't delete. Try again.")).toBeTruthy();
        expect(screen.getByText('the reply text')).toBeTruthy();
        expect(onRemoved).not.toHaveBeenCalled();
    });
});
