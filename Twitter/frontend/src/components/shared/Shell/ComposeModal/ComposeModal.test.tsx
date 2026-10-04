import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../../api/http';
import { publishTweet } from '../../../../api/tweets';
import type { PublishedTweet } from '../../../../api/tweets';
import { SIGNAL_LOST_MESSAGE } from '../../../../utils/authErrors';
import { MAX_IMAGE_BYTES } from '../../../../utils/composeChecks';
import ComposeModal from './ComposeModal';


vi.mock('../../../../api/tweets', () => ({
    publishTweet: vi.fn(),
}));

const AUTOFOCUS_DELAY_MS = 80;

const PUBLISHED: PublishedTweet = {
    id: 'tweet-1',
    authorId: 'user-1',
    content: 'hello world',
    createdAt: '2026-10-04T12:00:00Z',
    updatedAt: '2026-10-04T12:00:00Z',
    images: [],
};

const onClose = vi.fn();

const onPublished = vi.fn();

let user: ReturnType<typeof userEvent.setup>;

async function advance(milliseconds: number) {
    await act(async () => {
        await vi.advanceTimersByTimeAsync(milliseconds);
    });
}

async function openModal() {
    render(<ComposeModal onClose={onClose} onPublished={onPublished} />);

    await advance(AUTOFOCUS_DELAY_MS);
}

function textarea(): HTMLTextAreaElement {
    return screen.getByRole<HTMLTextAreaElement>('textbox', { name: "What's worth sending up?" });
}

function publishButton(): HTMLElement {
    return screen.getByRole('button', { name: /^PUBLISH/ });
}

function fileInput(): HTMLInputElement {
    return screen.getByLabelText<HTMLInputElement>('ATTACH IMAGE');
}

function picture(name = 'a.png', type = 'image/png'): File {
    return new File(
        ['pixels'],
        name,
        { type },
    );
}

// A rejected request settles a few promise steps after the click, so the clock is moved a tick to let it finish.
async function clickPublish() {
    await user.click(publishButton());
    await advance(1);
}

async function typeText(text: string) {
    await user.click(textarea());
    await user.paste(text);
}

beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    user = userEvent.setup({
        advanceTimers: vi.advanceTimersByTime,
        applyAccept: false,
    });
    vi.mocked(publishTweet)
        .mockReset()
        .mockResolvedValue(PUBLISHED);
    onClose.mockReset();
    onPublished.mockReset();
});

afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
});

describe('the dialog', () => {
    it('should_be_a_modal_dialog_named_by_its_headline', async () => {
        await openModal();

        const dialog = screen.getByRole('dialog', { name: "What's worth sending up?" });

        expect(dialog.getAttribute('aria-modal')).toBe('true');
    });

    it('should_show_the_copy_of_the_brief', async () => {
        await openModal();

        expect(screen.getByText('NEW POST')).toBeTruthy();
        expect(screen.getByRole('heading', { name: "What's worth sending up?" })).toBeTruthy();
        expect(textarea().getAttribute('placeholder')).toBe('write something');
        expect(screen.getByLabelText('ATTACH IMAGE')).toBeTruthy();
        expect(screen.getByText('0 / 280')).toBeTruthy();
        expect(screen.getByRole('button', { name: 'CANCEL' })).toBeTruthy();
    });

    it('should_hint_the_control_shortcut_on_a_pc', async () => {
        vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (Windows NT 10.0; Win64; x64)');

        await openModal();

        expect(publishButton().textContent).toBe('PUBLISH CTRL↵');
    });

    it('should_hint_the_command_shortcut_on_a_mac', async () => {
        vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)');

        await openModal();

        expect(publishButton().textContent).toBe('PUBLISH ⌘↵');
    });

    it('should_lock_the_scroll_of_the_page_while_open_and_release_it_when_closed', async () => {
        const { unmount } = render(<ComposeModal onClose={onClose} onPublished={onPublished} />);
        expect(document.documentElement.dataset.scrollLocked).toBe('true');

        unmount();

        expect(document.documentElement.dataset.scrollLocked).toBeUndefined();
    });

    it('should_not_focus_the_textarea_before_80_ms_and_focus_it_after', async () => {
        render(<ComposeModal onClose={onClose} onPublished={onPublished} />);

        await advance(AUTOFOCUS_DELAY_MS - 1);
        expect(document.activeElement).not.toBe(textarea());

        await advance(1);
        expect(document.activeElement).toBe(textarea());
    });

    it('should_not_focus_after_the_modal_is_gone', async () => {
        const { unmount } = render(<ComposeModal onClose={onClose} onPublished={onPublished} />);

        unmount();
        await advance(AUTOFOCUS_DELAY_MS * 2);

        expect(document.activeElement).toBe(document.body);
    });
});

describe('the counter', () => {
    it('should_count_what_is_typed', async () => {
        await openModal();

        await typeText('hello');

        expect(screen.getByText('5 / 280')).toBeTruthy();
    });

    it('should_not_count_the_spaces_around_the_text', async () => {
        await openModal();

        await typeText('  hello  ');

        expect(screen.getByText('5 / 280')).toBeTruthy();
    });

    it('should_count_an_emoji_as_one_character', async () => {
        await openModal();

        await typeText('😀😀');

        expect(screen.getByText('2 / 280')).toBeTruthy();
    });

    it('should_not_mark_the_counter_at_exactly_280_characters', async () => {
        await openModal();

        await typeText('a'.repeat(280));

        expect(screen.getByText('280 / 280').getAttribute('data-over-limit')).toBe('false');
    });

    it('should_mark_the_counter_past_280_characters', async () => {
        await openModal();

        await typeText('a'.repeat(281));

        expect(screen.getByText('281 / 280').getAttribute('data-over-limit')).toBe('true');
    });
});

describe('when publishing is allowed', () => {
    it('should_be_refused_when_there_is_no_text_and_no_image', async () => {
        await openModal();

        expect(publishButton().getAttribute('aria-disabled')).toBe('true');
    });

    it('should_be_refused_when_the_text_is_only_spaces', async () => {
        await openModal();

        await typeText('   ');

        expect(publishButton().getAttribute('aria-disabled')).toBe('true');
    });

    it('should_be_allowed_when_there_is_text', async () => {
        await openModal();

        await typeText('hello');

        expect(publishButton().getAttribute('aria-disabled')).toBe('false');
    });

    it('should_be_allowed_when_there_is_only_an_image', async () => {
        await openModal();

        await user.upload(fileInput(), picture());

        expect(publishButton().getAttribute('aria-disabled')).toBe('false');
    });

    it('should_be_refused_when_the_text_is_over_280_characters', async () => {
        await openModal();

        await typeText('a'.repeat(281));

        expect(publishButton().getAttribute('aria-disabled')).toBe('true');
    });

    it('should_send_nothing_when_a_refused_publish_is_clicked', async () => {
        await openModal();

        await user.click(publishButton());

        expect(publishTweet).not.toHaveBeenCalled();
    });
});

describe('images', () => {
    it('should_show_a_preview_with_a_labelled_remove_button_when_a_picture_is_picked', async () => {
        await openModal();

        await user.upload(fileInput(), picture('a.png'));

        expect(screen.getByRole('img', { name: 'Attached image 1' }).getAttribute('src')).toBe('blob:preview-a.png');
        expect(screen.getByRole('button', { name: 'Remove image 1' })).toBeTruthy();
    });

    it('should_remove_the_preview_and_release_its_url_when_remove_is_pressed', async () => {
        await openModal();
        await user.upload(fileInput(), picture('a.png'));

        await user.click(screen.getByRole('button', { name: 'Remove image 1' }));

        expect(screen.queryByRole('img')).toBeNull();
        expect(URL.revokeObjectURL).toHaveBeenCalledExactlyOnceWith('blob:preview-a.png');
    });

    it('should_release_the_preview_urls_when_the_modal_closes', async () => {
        const { unmount } = render(<ComposeModal onClose={onClose} onPublished={onPublished} />);
        await user.upload(fileInput(), picture('a.png'));

        unmount();

        expect(URL.revokeObjectURL).toHaveBeenCalledExactlyOnceWith('blob:preview-a.png');
    });

    it('should_show_the_error_next_to_the_attach_control_when_a_fifth_picture_is_picked', async () => {
        await openModal();

        await user.upload(fileInput(), ['1', '2', '3', '4', '5'].map((name) => picture(`${name}.png`)));

        expect(screen.getAllByRole('img')).toHaveLength(4);
        expect(screen.getByText('A TWEET CAN HAVE AT MOST 4 IMAGES')).toBeTruthy();
    });

    it('should_show_the_error_when_the_picture_is_not_a_jpeg_png_or_webp', async () => {
        await openModal();

        await user.upload(
            fileInput(),
            picture('a.gif', 'image/gif'),
        );

        expect(screen.queryByRole('img')).toBeNull();
        expect(screen.getByText('UNSUPPORTED IMAGE TYPE')).toBeTruthy();
    });

    it('should_show_the_error_when_the_picture_is_over_5_mib', async () => {
        await openModal();
        const huge = picture('huge.png');
        Object.defineProperty(
            huge,
            'size',
            { value: MAX_IMAGE_BYTES + 1 },
        );

        await user.upload(fileInput(), huge);

        expect(screen.queryByRole('img')).toBeNull();
        expect(screen.getByText('THE UPLOADED FILE IS TOO LARGE')).toBeTruthy();
    });

    it('should_clear_the_error_when_a_valid_picture_is_picked_afterwards', async () => {
        await openModal();
        await user.upload(
            fileInput(),
            picture('a.gif', 'image/gif'),
        );

        await user.upload(fileInput(), picture('b.png'));

        expect(screen.queryByText('UNSUPPORTED IMAGE TYPE')).toBeNull();
    });

    it('should_accept_the_same_file_again_after_it_was_removed', async () => {
        await openModal();
        const file = picture('a.png');
        await user.upload(fileInput(), file);
        await user.click(screen.getByRole('button', { name: 'Remove image 1' }));

        await user.upload(fileInput(), file);

        expect(screen.getAllByRole('img')).toHaveLength(1);
    });

    it('should_offer_only_jpeg_png_and_webp_in_the_file_picker', async () => {
        await openModal();

        expect(fileInput().getAttribute('accept')).toBe('image/jpeg,image/png,image/webp');
    });
});

describe('publishing', () => {
    it('should_send_the_trimmed_text_and_the_pictures', async () => {
        await openModal();
        const file = picture('a.png');
        await typeText('  hello world  ');
        await user.upload(fileInput(), file);

        await user.click(publishButton());

        expect(publishTweet).toHaveBeenCalledExactlyOnceWith({
            content: 'hello world',
            images: [file],
        });
    });

    it('should_hand_the_published_tweet_on_when_the_server_accepts_it', async () => {
        await openModal();
        await typeText('hello world');

        await user.click(publishButton());

        expect(onPublished).toHaveBeenCalledExactlyOnceWith(PUBLISHED);
    });

    it('should_publish_when_command_and_enter_are_pressed', async () => {
        await openModal();
        await typeText('hello world');

        await user.keyboard('{Meta>}{Enter}{/Meta}');

        expect(publishTweet).toHaveBeenCalledTimes(1);
    });

    it('should_publish_when_control_and_enter_are_pressed', async () => {
        await openModal();
        await typeText('hello world');

        await user.keyboard('{Control>}{Enter}{/Control}');

        expect(publishTweet).toHaveBeenCalledTimes(1);
    });

    it('should_not_publish_when_enter_alone_is_pressed', async () => {
        await openModal();
        await typeText('hello world');

        await user.keyboard('{Enter}');

        expect(publishTweet).not.toHaveBeenCalled();
        expect(textarea().value).toBe('hello world\n');
    });

    it('should_not_publish_by_shortcut_when_publishing_is_refused', async () => {
        await openModal();

        await user.keyboard('{Control>}{Enter}{/Control}');

        expect(publishTweet).not.toHaveBeenCalled();
    });

    it('should_send_one_request_when_the_shortcut_and_the_button_are_used_together', async () => {
        vi.mocked(publishTweet).mockReturnValue(new Promise<PublishedTweet>(() => undefined));
        await openModal();
        await typeText('hello world');

        await user.keyboard('{Control>}{Enter}{/Control}');
        await user.click(publishButton());

        expect(publishTweet).toHaveBeenCalledTimes(1);
    });

    it('should_show_the_servers_words_next_to_publish_and_keep_the_draft_when_the_post_is_refused', async () => {
        vi.mocked(publishTweet).mockRejectedValue(new ApiError(400, ['A tweet can have at most 4 images.']));
        await openModal();
        await typeText('hello world');
        await user.upload(fileInput(), picture('a.png'));

        await clickPublish();

        expect(screen.getByText('A TWEET CAN HAVE AT MOST 4 IMAGES')).toBeTruthy();
        expect(textarea().value).toBe('hello world');
        expect(screen.getAllByRole('img')).toHaveLength(1);
        expect(publishButton().getAttribute('aria-disabled')).toBe('false');
        expect(onPublished).not.toHaveBeenCalled();
    });

    it.each([0, 500, 502])('should_say_signal_lost_when_the_status_is_%i', async (status) => {
        vi.mocked(publishTweet).mockRejectedValue(new ApiError(status, []));
        await openModal();
        await typeText('hello world');

        await clickPublish();

        expect(screen.getByText(SIGNAL_LOST_MESSAGE)).toBeTruthy();
        expect(textarea().value).toBe('hello world');
    });

    it('should_publish_again_when_the_publish_button_is_pressed_after_a_failure', async () => {
        vi.mocked(publishTweet).mockRejectedValueOnce(new ApiError(500, []));
        await openModal();
        await typeText('hello world');
        await clickPublish();

        await clickPublish();

        expect(publishTweet).toHaveBeenCalledTimes(2);
        expect(onPublished).toHaveBeenCalledTimes(1);
    });

    it('should_mark_publish_disabled_while_the_request_is_on_its_way', async () => {
        vi.mocked(publishTweet).mockReturnValue(new Promise<PublishedTweet>(() => undefined));
        await openModal();
        await typeText('hello world');

        await user.click(publishButton());

        expect(publishButton().getAttribute('aria-disabled')).toBe('true');
    });

    it('should_ignore_cancel_and_escape_while_the_request_is_on_its_way', async () => {
        vi.mocked(publishTweet).mockReturnValue(new Promise<PublishedTweet>(() => undefined));
        await openModal();
        await typeText('hello world');
        await user.click(publishButton());

        await user.click(screen.getByRole('button', { name: 'CANCEL' }));
        await user.keyboard('{Escape}');

        expect(onClose).not.toHaveBeenCalled();
        expect(screen.queryByText('DISCARD POST?')).toBeNull();
    });
});

describe('closing', () => {
    it('should_close_at_once_when_cancel_is_pressed_with_an_empty_draft', async () => {
        await openModal();

        await user.click(screen.getByRole('button', { name: 'CANCEL' }));

        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('should_close_at_once_when_escape_is_pressed_with_an_empty_draft', async () => {
        await openModal();

        await user.keyboard('{Escape}');

        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('should_close_at_once_when_the_draft_is_only_spaces', async () => {
        await openModal();
        await typeText('   ');

        await user.keyboard('{Escape}');

        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('should_ask_before_discarding_when_cancel_is_pressed_with_text', async () => {
        await openModal();
        await typeText('hello');

        await user.click(screen.getByRole('button', { name: 'CANCEL' }));

        expect(onClose).not.toHaveBeenCalled();
        expect(screen.getByText('DISCARD POST?')).toBeTruthy();
        expect(screen.getByRole('button', { name: 'DISCARD' })).toBeTruthy();
        expect(screen.getByRole('button', { name: 'KEEP EDITING' })).toBeTruthy();
        expect(screen.queryByRole('button', { name: /^PUBLISH/ })).toBeNull();
    });

    it('should_ask_before_discarding_when_escape_is_pressed_with_text', async () => {
        await openModal();
        await typeText('hello');

        await user.keyboard('{Escape}');

        expect(onClose).not.toHaveBeenCalled();
        expect(screen.getByText('DISCARD POST?')).toBeTruthy();
    });

    it('should_ask_before_discarding_when_there_is_only_an_image', async () => {
        await openModal();
        await user.upload(fileInput(), picture());

        await user.keyboard('{Escape}');

        expect(screen.getByText('DISCARD POST?')).toBeTruthy();
    });

    it('should_not_open_a_browser_dialog_when_the_draft_is_discarded', async () => {
        const confirm = vi.spyOn(window, 'confirm');
        await openModal();
        await typeText('hello');

        await user.click(screen.getByRole('button', { name: 'CANCEL' }));
        await user.click(screen.getByRole('button', { name: 'DISCARD' }));

        expect(confirm).not.toHaveBeenCalled();
    });

    it('should_move_focus_to_keep_editing_when_the_question_appears', async () => {
        await openModal();
        await typeText('hello');

        await user.click(screen.getByRole('button', { name: 'CANCEL' }));

        expect(document.activeElement).toBe(screen.getByRole('button', { name: 'KEEP EDITING' }));
    });

    it('should_close_when_discard_is_pressed', async () => {
        await openModal();
        await typeText('hello');
        await user.click(screen.getByRole('button', { name: 'CANCEL' }));

        await user.click(screen.getByRole('button', { name: 'DISCARD' }));

        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('should_go_back_to_the_draft_with_the_text_kept_and_textarea_focused_when_keep_editing_is_pressed', async () => {
        await openModal();
        await typeText('hello');
        await user.click(screen.getByRole('button', { name: 'CANCEL' }));

        await user.click(screen.getByRole('button', { name: 'KEEP EDITING' }));

        expect(screen.queryByText('DISCARD POST?')).toBeNull();
        expect(textarea().value).toBe('hello');
        expect(document.activeElement).toBe(textarea());
        expect(onClose).not.toHaveBeenCalled();
    });

    it('should_keep_editing_and_not_close_when_escape_is_pressed_at_the_question', async () => {
        await openModal();
        await typeText('hello');
        await user.keyboard('{Escape}');

        await user.keyboard('{Escape}');

        expect(onClose).not.toHaveBeenCalled();
        expect(screen.queryByText('DISCARD POST?')).toBeNull();
        expect(document.activeElement).toBe(textarea());
    });

    it('should_not_publish_by_shortcut_while_the_question_is_shown', async () => {
        await openModal();
        await typeText('hello');
        await user.click(screen.getByRole('button', { name: 'CANCEL' }));

        await user.keyboard('{Control>}{Enter}{/Control}');

        expect(publishTweet).not.toHaveBeenCalled();
    });
});

describe('the focus trap', () => {
    it('should_wrap_from_the_last_control_to_the_first_when_tab_is_pressed', async () => {
        await openModal();
        screen.getByRole('button', { name: 'CANCEL' }).focus();

        await user.tab();

        expect(document.activeElement).toBe(textarea());
    });

    it('should_wrap_from_the_first_control_to_the_last_when_shift_tab_is_pressed', async () => {
        await openModal();

        await user.tab({ shift: true });

        expect(document.activeElement).toBe(screen.getByRole('button', { name: 'CANCEL' }));
    });

    it('should_move_between_the_controls_in_the_middle_as_usual', async () => {
        await openModal();

        await user.tab();

        expect(document.activeElement).toBe(fileInput());
    });

    it('should_keep_focus_inside_after_many_tabs', async () => {
        await openModal();

        for (let press = 0; press < 12; press++) {
            await user.tab();
        }

        expect(screen.getByRole('dialog').contains(document.activeElement)).toBe(true);
    });

    it('should_wrap_inside_the_question_row_as_well', async () => {
        await openModal();
        await typeText('hello');
        await user.click(screen.getByRole('button', { name: 'CANCEL' }));
        screen.getByRole('button', { name: 'KEEP EDITING' }).focus();

        await user.tab();

        expect(document.activeElement).toBe(textarea());
    });
});
