import { act, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ENDPOINTS } from '../../../api/endpoints';
import { ApiError } from '../../../api/http';
import { updateProfile, uploadProfilePicture } from '../../../api/users';
import { advance } from '../../../test/stepFlowHelpers';
import { userProfile } from '../../../test/userProfile';
import ProfileEditSheet from './ProfileEditSheet';


vi.mock('../../../api/users', () => ({
    updateProfile: vi.fn(),
    uploadProfilePicture: vi.fn(),
}));

const PROFILE = userProfile({
    username: 'peter_g',
    bio: 'Builds boats.',
    location: 'Sofia',
});

const AUTOFOCUS_MS = 80;

const DISARM_MS = 3000;

const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

// The browser's own picker filters by `accept`; a dropped file or a wrong label does not, so the check runs anyway.
const userWithoutAcceptFilter = userEvent.setup({
    advanceTimers: vi.advanceTimersByTime,
    applyAccept: false,
});

function renderSheet(changes: Partial<ReturnType<typeof userProfile>> = {}) {
    const onSaved = vi.fn();
    const onClose = vi.fn();

    render(
        <ProfileEditSheet
            profile={{ ...PROFILE, ...changes }}
            email="peter@example.com"
            onSaved={onSaved}
            onClose={onClose}
        />,
    );

    return {
        onSaved,
        onClose,
    };
}

function bioField(): HTMLTextAreaElement {
    const field = screen.getByRole('textbox', { name: 'BIO' });

    if (!(field instanceof HTMLTextAreaElement)) {
        throw new Error('Expected the bio to be a textarea.');
    }

    return field;
}

function locationField(): HTMLInputElement {
    const field = screen.getByRole('textbox', { name: 'LOCATION' });

    if (!(field instanceof HTMLInputElement)) {
        throw new Error('Expected the location to be an input.');
    }

    return field;
}

function photoInput(): HTMLInputElement {
    const input = screen.getByLabelText('CHANGE PHOTO');

    if (!(input instanceof HTMLInputElement)) {
        throw new Error('Expected the photo picker to be a file input.');
    }

    return input;
}

function alertTexts(): string[] {
    return screen
        .getAllByRole('alert')
        .map((alert) => alert.textContent ?? '');
}

function areAlertsEmpty(): boolean {
    return alertTexts()
        .every((text) => text === '');
}

function previewSrc(): string | null {
    return document.querySelector('.profile-edit-sheet-photo img')?.getAttribute('src') ?? null;
}

function photoFile(name = 'me.png', type = 'image/png', size = 3): File {
    const file = new File(['abc'], name, { type });

    Object.defineProperty(file, 'size', { value: size });

    return file;
}

interface SheetInPageProps {
    onClose: () => void;
}

// A parent that keeps the saved profile, as the page does, so the sheet's baseline moves when a save goes through.
function SheetInPage({ onClose }: SheetInPageProps) {
    const [profile, setProfile] = useState(PROFILE);

    return (
        <ProfileEditSheet
            profile={profile}
            email="a@b.c"
            onSaved={setProfile}
            onClose={onClose}
        />
    );
}

function saveButton(): HTMLElement {
    return screen.getByRole('button', { name: /^SAV/ });
}

function cancelButton(): HTMLElement {
    return screen.getByRole('button', { name: /CANCEL|DISCARD/ });
}

async function replaceText(field: HTMLElement, text: string) {
    await user.clear(field);

    if (text !== '') {
        await user.click(field);
        await user.paste(text);
    }
}

// A request that stays in flight until the test settles it.
function holdSave() {
    let resolveRequest = (savedProfile: ReturnType<typeof userProfile>) => {
        void savedProfile;
    };
    let rejectRequest = (failure: unknown) => {
        void failure;
    };
    const pendingRequest = new Promise<ReturnType<typeof userProfile>>((resolve, reject) => {
        resolveRequest = resolve;
        rejectRequest = reject;
    });

    vi.mocked(updateProfile)
        .mockReturnValueOnce(pendingRequest);

    return {
        resolve: (savedProfile: ReturnType<typeof userProfile>) => act(async () => resolveRequest(savedProfile)),
        reject: (failure: unknown) => act(async () => rejectRequest(failure)),
    };
}

beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'requestAnimationFrame', 'cancelAnimationFrame'] });
    vi.mocked(updateProfile)
        .mockReset()
        .mockResolvedValue(PROFILE);
    vi.mocked(uploadProfilePicture)
        .mockReset()
        .mockResolvedValue(PROFILE);
    vi.mocked(URL.revokeObjectURL).mockClear();
});

afterEach(() => {
    vi.useRealTimers();
});

describe('the dialog', () => {
    it('should_be_a_modal_dialog_named_by_its_headline', () => {
        renderSheet();

        const dialog = screen.getByRole('dialog');

        expect(dialog.getAttribute('aria-modal')).toBe('true');
        expect(within(dialog).getByRole('heading', { name: 'Say who you are.' })).toBeTruthy();
        expect(dialog.getAttribute('aria-labelledby')).toBe(within(dialog).getByRole('heading').id);
    });

    it('should_focus_the_bio_after_a_moment_and_not_before', async () => {
        renderSheet();

        expect(document.activeElement).not.toBe(bioField());

        await advance(AUTOFOCUS_MS);

        expect(document.activeElement).toBe(bioField());
    });

    it('should_lock_the_scroll_of_the_page_while_open_and_release_it_when_closed', () => {
        const { unmount } = render(
            <ProfileEditSheet
                profile={PROFILE}
                email="a@b.c"
                onSaved={vi.fn()}
                onClose={vi.fn()}
            />,
        );

        expect(document.documentElement.dataset.scrollLocked).toBe('true');

        unmount();

        expect(document.documentElement.dataset.scrollLocked).toBeUndefined();
    });

    it('should_keep_tab_inside_the_dialog_by_wrapping_from_the_last_control_to_the_first', async () => {
        renderSheet();
        cancelButton().focus();

        await user.tab();

        expect(document.activeElement).toBe(photoInput());
    });

    it('should_keep_shift_tab_inside_the_dialog_by_wrapping_from_the_first_control_to_the_last', async () => {
        renderSheet();
        photoInput().focus();

        await user.tab({ shift: true });

        expect(document.activeElement).toBe(cancelButton());
    });
});

describe('the locked fields', () => {
    it('should_show_the_email_and_the_username_as_text_with_a_locked_tag_and_no_input', () => {
        renderSheet();

        expect(screen.getByText('peter@example.com')).toBeTruthy();
        expect(screen.getByText('peter_g')).toBeTruthy();
        expect(screen.getAllByText('LOCKED')).toHaveLength(2);
        expect(screen.getAllByRole('textbox')).toHaveLength(2);
    });

    it('should_say_why_each_one_cannot_change_to_a_screen_reader', () => {
        renderSheet();

        expect(screen.getByText('Email cannot be changed here')).toBeTruthy();
        expect(screen.getByText('Username cannot be changed')).toBeTruthy();
    });

    it('should_hide_the_visual_tag_from_assistive_technology', () => {
        renderSheet();

        screen
            .getAllByText('LOCKED')
            .forEach((tag) => {
                expect(tag.getAttribute('aria-hidden')).toBe('true');
            });
    });
});

describe('the fields', () => {
    it('should_start_with_the_saved_bio_and_location', () => {
        renderSheet();

        expect(bioField().value).toBe('Builds boats.');
        expect(locationField().value).toBe('Sofia');
    });

    it('should_start_empty_when_the_profile_has_no_bio_and_no_location', () => {
        renderSheet({
            bio: null,
            location: null,
        });

        expect(bioField().value).toBe('');
        expect(locationField().value).toBe('');
        expect(locationField().getAttribute('placeholder')).toBe('City, Country');
    });

    it('should_count_the_trimmed_code_points_of_the_bio', async () => {
        renderSheet({ bio: null });

        await replaceText(bioField(), '  \u{1F600}\u{1F600}a  ');

        expect(screen.getByText('3 / 160')).toBeTruthy();
    });

    it('should_turn_the_counter_to_the_alarm_state_past_160_and_block_saving', async () => {
        renderSheet({ bio: null });

        await replaceText(bioField(), 'a'.repeat(161));

        expect(screen.getByText('161 / 160').getAttribute('data-over-limit')).toBe('true');
        expect(saveButton().getAttribute('aria-disabled')).toBe('true');
        expect(bioField().getAttribute('aria-invalid')).toBe('true');
    });

    it('should_accept_exactly_160_characters', async () => {
        renderSheet({ bio: null });

        await replaceText(bioField(), 'a'.repeat(160));

        expect(screen.getByText('160 / 160').getAttribute('data-over-limit')).toBe('false');
        expect(saveButton().getAttribute('aria-disabled')).toBe('false');
    });

    it('should_show_an_error_under_the_location_and_block_saving_past_60_characters', async () => {
        renderSheet({ location: null });

        await replaceText(locationField(), 'a'.repeat(61));

        expect(screen.getByText('LOCATION MUST BE AT MOST 60 CHARACTERS')).toBeTruthy();
        expect(saveButton().getAttribute('aria-disabled')).toBe('true');
    });

    it('should_stay_quiet_for_screen_readers_while_typing_and_speak_only_when_the_limit_is_crossed', async () => {
        renderSheet({ bio: null });
        const liveRegion = () => screen
            .getAllByText((_text, element) => element?.getAttribute('aria-live') === 'polite')
            .map((region) => region.textContent)
            .join('');

        await replaceText(bioField(), 'a'.repeat(150));
        expect(liveRegion()).toBe('');

        await user.paste('a'.repeat(11));
        expect(liveRegion()).toBe('Bio is over the 160 character limit.');

        await user.paste('a');
        expect(liveRegion()).toBe('Bio is over the 160 character limit.');

        await user.keyboard('{Backspace>12/}');
        expect(liveRegion()).toBe('Bio is within the limit.');
    });
});

describe('saving', () => {
    it('should_close_without_a_request_when_nothing_changed', async () => {
        const { onClose, onSaved } = renderSheet();

        await user.click(saveButton());

        expect(updateProfile).not.toHaveBeenCalled();
        expect(onClose).toHaveBeenCalledOnce();
        expect(onSaved).not.toHaveBeenCalled();
    });

    it('should_close_without_a_request_when_only_blanks_around_the_text_changed', async () => {
        const { onClose } = renderSheet();

        await replaceText(bioField(), '  Builds boats.  ');
        await user.click(saveButton());

        expect(updateProfile).not.toHaveBeenCalled();
        expect(onClose).toHaveBeenCalledOnce();
    });

    it('should_send_only_the_location_when_only_the_location_changed', async () => {
        renderSheet();

        await replaceText(locationField(), 'Plovdiv');
        await user.click(saveButton());

        expect(updateProfile).toHaveBeenCalledExactlyOnceWith({ location: 'Plovdiv' });
    });

    it('should_send_only_the_bio_trimmed_when_only_the_bio_changed', async () => {
        renderSheet();

        await replaceText(bioField(), '  New bio  ');
        await user.click(saveButton());

        expect(updateProfile).toHaveBeenCalledExactlyOnceWith({ bio: 'New bio' });
    });

    it('should_send_an_empty_string_when_a_field_was_cleared', async () => {
        renderSheet();

        await replaceText(locationField(), '');
        await user.click(saveButton());

        expect(updateProfile).toHaveBeenCalledExactlyOnceWith({ location: '' });
    });

    it('should_save_with_enter_in_the_location_field', async () => {
        renderSheet();

        await replaceText(locationField(), 'Plovdiv');
        await user.keyboard('{Enter}');

        expect(updateProfile).toHaveBeenCalledExactlyOnceWith({ location: 'Plovdiv' });
    });

    it('should_hand_the_saved_profile_over_and_close_when_the_server_accepts', async () => {
        const savedProfile = userProfile({
            ...PROFILE,
            location: 'Plovdiv',
        });
        vi.mocked(updateProfile)
            .mockResolvedValue(savedProfile);
        const { onSaved, onClose } = renderSheet();

        await replaceText(locationField(), 'Plovdiv');
        await user.click(saveButton());

        expect(onSaved).toHaveBeenCalledExactlyOnceWith(savedProfile);
        expect(onClose).toHaveBeenCalledOnce();
    });

    it('should_show_a_pending_state_and_send_one_request_when_save_is_pressed_twice', async () => {
        const request = holdSave();
        renderSheet();
        await replaceText(locationField(), 'Plovdiv');

        await user.click(saveButton());
        await user.click(saveButton());

        expect(saveButton().textContent).toBe('SAVING');
        expect(saveButton().getAttribute('aria-busy')).toBe('true');
        expect(saveButton().getAttribute('aria-disabled')).toBe('true');
        expect(updateProfile).toHaveBeenCalledTimes(1);

        await request.resolve(PROFILE);
    });

    it('should_keep_the_sheet_open_the_draft_and_the_error_under_the_bio_when_the_server_refuses_the_bio', async () => {
        vi.mocked(updateProfile)
            .mockRejectedValue(new ApiError(400, ['bio must be at most 160 characters']));
        const { onClose, onSaved } = renderSheet();

        await replaceText(bioField(), 'New bio');
        await user.click(saveButton());
        await advance(1);

        expect(screen.getByText('BIO MUST BE AT MOST 160 CHARACTERS')).toBeTruthy();
        expect(bioField().value).toBe('New bio');
        expect(onClose).not.toHaveBeenCalled();
        expect(onSaved).not.toHaveBeenCalled();
        expect(saveButton().textContent).toBe('SAVE CHANGES');
    });

    it('should_show_signal_lost_beside_the_buttons_when_the_request_fails', async () => {
        vi.mocked(updateProfile)
            .mockRejectedValue(new ApiError(502, []));
        renderSheet();

        await replaceText(locationField(), 'Plovdiv');
        await user.click(saveButton());
        await advance(1);

        expect(alertTexts()).toContain('SIGNAL LOST — TRY AGAIN');
        expect(locationField().value).toBe('Plovdiv');
    });

    it('should_clear_the_field_error_when_the_field_is_edited_and_allow_a_new_try', async () => {
        vi.mocked(updateProfile)
            .mockRejectedValueOnce(new ApiError(400, ['bio must be at most 160 characters']))
            .mockResolvedValueOnce(PROFILE);
        const { onClose } = renderSheet();
        await replaceText(bioField(), 'New bio');
        await user.click(saveButton());
        await advance(1);
        expect(screen.getByText('BIO MUST BE AT MOST 160 CHARACTERS')).toBeTruthy();

        await user.click(bioField());
        await user.paste('!');

        expect(screen.queryByText('BIO MUST BE AT MOST 160 CHARACTERS')).toBeNull();

        await user.click(saveButton());

        expect(updateProfile).toHaveBeenCalledTimes(2);
        expect(onClose).toHaveBeenCalledOnce();
    });
});

describe('cancelling', () => {
    it('should_close_at_once_when_nothing_was_changed', async () => {
        const { onClose } = renderSheet();

        await user.click(cancelButton());

        expect(onClose).toHaveBeenCalledOnce();
    });

    it('should_close_at_once_on_escape_when_nothing_was_changed', async () => {
        const { onClose } = renderSheet();
        await advance(AUTOFOCUS_MS);

        await user.keyboard('{Escape}');

        expect(onClose).toHaveBeenCalledOnce();
    });

    it('should_ask_discard_changes_in_place_on_the_first_press_when_something_changed', async () => {
        const { onClose } = renderSheet();
        await replaceText(locationField(), 'Plovdiv');

        await user.click(cancelButton());

        expect(cancelButton().textContent).toBe('DISCARD CHANGES?');
        expect(onClose).not.toHaveBeenCalled();
        expect(screen.getByText('Press again to discard your changes')).toBeTruthy();
    });

    it('should_discard_on_the_second_press', async () => {
        const { onClose } = renderSheet();
        await replaceText(locationField(), 'Plovdiv');

        await user.click(cancelButton());
        await user.click(cancelButton());

        expect(onClose).toHaveBeenCalledOnce();
        expect(updateProfile).not.toHaveBeenCalled();
    });

    it('should_ask_on_the_first_escape_and_discard_on_the_second', async () => {
        const { onClose } = renderSheet();
        await replaceText(locationField(), 'Plovdiv');

        await user.keyboard('{Escape}');

        expect(cancelButton().textContent).toBe('DISCARD CHANGES?');
        expect(onClose).not.toHaveBeenCalled();

        await user.keyboard('{Escape}');

        expect(onClose).toHaveBeenCalledOnce();
    });

    it('should_go_back_to_cancel_after_three_seconds', async () => {
        const { onClose } = renderSheet();
        await advance(AUTOFOCUS_MS);
        await replaceText(locationField(), 'Plovdiv');
        await user.click(cancelButton());

        await advance(DISARM_MS - 1);
        expect(cancelButton().textContent).toBe('DISCARD CHANGES?');

        await advance(1);
        expect(cancelButton().textContent).toBe('CANCEL ESC');
        expect(onClose).not.toHaveBeenCalled();
    });

    it('should_go_back_to_cancel_when_the_button_loses_focus', async () => {
        renderSheet();
        await replaceText(locationField(), 'Plovdiv');
        await user.click(cancelButton());

        fireEvent.blur(cancelButton());

        expect(cancelButton().textContent).toBe('CANCEL ESC');
    });

    it('should_go_back_to_cancel_when_the_user_keeps_typing', async () => {
        renderSheet();
        await replaceText(locationField(), 'Plovdiv');
        await user.click(cancelButton());

        await user.click(locationField());
        await user.paste('!');

        expect(cancelButton().textContent).toBe('CANCEL ESC');
    });

    it('should_ask_when_a_field_was_changed_and_changed_back_no_longer', async () => {
        const { onClose } = renderSheet();
        await replaceText(locationField(), 'Plovdiv');
        await replaceText(locationField(), 'Sofia');

        await user.click(cancelButton());

        expect(onClose).toHaveBeenCalledOnce();
    });

    it('should_do_nothing_on_cancel_or_escape_while_saving', async () => {
        const request = holdSave();
        const { onClose } = renderSheet();
        await replaceText(locationField(), 'Plovdiv');
        await user.click(saveButton());

        await user.click(cancelButton());
        await user.keyboard('{Escape}');

        expect(onClose).not.toHaveBeenCalled();

        await request.resolve(PROFILE);
    });
});

describe('the photo', () => {
    it('should_show_the_current_picture_and_the_hint_with_the_type_and_size_limits', () => {
        renderSheet({ profilePictureUrl: 'http://localhost/api/v1/files/pic-1' });

        expect(previewSrc()).toBe('http://localhost/api/v1/files/pic-1');
        expect(screen.getByText('JPG, PNG OR WEBP · MAX 5 MB')).toBeTruthy();
        expect(photoInput().getAttribute('accept')).toBe('image/jpeg,image/png,image/webp');
    });

    it('should_show_the_default_picture_when_the_profile_has_none', () => {
        renderSheet();

        expect(previewSrc()).toBe(ENDPOINTS.defaultProfilePicture);
    });

    it('should_show_the_picked_photo_at_once_before_anything_is_sent', async () => {
        renderSheet();

        await user.upload(photoInput(), photoFile());

        expect(previewSrc()).toBe('blob:preview-me.png');
        expect(uploadProfilePicture).not.toHaveBeenCalled();
    });

    it.each([
        ['image/jpeg', 'me.jpg'],
        ['image/png', 'me.png'],
        ['image/webp', 'me.webp'],
    ])('should_accept_a_%s_photo', async (type, name) => {
        renderSheet();

        await user.upload(photoInput(), photoFile(name, type));

        expect(previewSrc()).toBe(`blob:preview-${name}`);
    });

    it('should_refuse_a_gif_with_a_message_by_the_row_and_keep_the_picture', async () => {
        renderSheet();

        await userWithoutAcceptFilter.upload(photoInput(), photoFile('anim.gif', 'image/gif'));

        expect(alertTexts()).toContain('UNSUPPORTED IMAGE TYPE');
        expect(previewSrc()).toBe(ENDPOINTS.defaultProfilePicture);
    });

    it('should_refuse_a_photo_over_5_mb_and_accept_one_of_exactly_5_mb', async () => {
        renderSheet();

        await user.upload(photoInput(), photoFile('big.png', 'image/png', 5_242_881));

        expect(alertTexts()).toContain('THE UPLOADED FILE IS TOO LARGE');
        expect(previewSrc()).toBe(ENDPOINTS.defaultProfilePicture);

        await user.upload(photoInput(), photoFile('edge.png', 'image/png', 5_242_880));

        expect(previewSrc()).toBe('blob:preview-edge.png');
        expect(areAlertsEmpty()).toBe(true);
    });

    it('should_keep_an_earlier_pick_when_a_later_one_is_refused', async () => {
        renderSheet();
        await user.upload(photoInput(), photoFile('first.png'));

        await userWithoutAcceptFilter.upload(photoInput(), photoFile('anim.gif', 'image/gif'));

        expect(previewSrc()).toBe('blob:preview-first.png');
        expect(alertTexts()).toContain('UNSUPPORTED IMAGE TYPE');
    });

    it('should_release_the_old_preview_when_a_new_photo_replaces_it_and_the_last_one_when_closed', async () => {
        const { unmount } = render(
            <ProfileEditSheet
                profile={PROFILE}
                email="a@b.c"
                onSaved={vi.fn()}
                onClose={vi.fn()}
            />,
        );

        await user.upload(photoInput(), photoFile('first.png'));
        await user.upload(photoInput(), photoFile('second.png'));

        expect(URL.revokeObjectURL).toHaveBeenCalledExactlyOnceWith('blob:preview-first.png');

        unmount();

        expect(URL.revokeObjectURL).toHaveBeenLastCalledWith('blob:preview-second.png');
    });

    it('should_count_a_picked_photo_as_a_change_when_cancelling', async () => {
        const { onClose } = renderSheet();
        await user.upload(photoInput(), photoFile());

        await user.click(cancelButton());

        expect(cancelButton().textContent).toBe('DISCARD CHANGES?');
        expect(onClose).not.toHaveBeenCalled();
    });

    it('should_upload_only_the_photo_and_not_patch_when_only_a_photo_was_picked', async () => {
        const savedProfile = userProfile({
            ...PROFILE,
            profilePictureUrl: 'http://localhost/api/v1/files/pic-9',
        });
        vi.mocked(uploadProfilePicture)
            .mockResolvedValue(savedProfile);
        const photo = photoFile();
        const { onSaved, onClose } = renderSheet();
        await user.upload(photoInput(), photo);

        await user.click(saveButton());

        expect(updateProfile).not.toHaveBeenCalled();
        expect(uploadProfilePicture).toHaveBeenCalledExactlyOnceWith(photo);
        expect(onSaved).toHaveBeenCalledExactlyOnceWith(savedProfile);
        expect(onClose).toHaveBeenCalledOnce();
    });

    it('should_patch_the_text_first_and_upload_the_photo_after_it', async () => {
        const photo = photoFile();
        const { onSaved } = renderSheet();
        await replaceText(locationField(), 'Plovdiv');
        await user.upload(photoInput(), photo);

        await user.click(saveButton());

        expect(vi.mocked(updateProfile).mock.invocationCallOrder[0])
            .toBeLessThan(vi.mocked(uploadProfilePicture).mock.invocationCallOrder[0]);
        expect(updateProfile).toHaveBeenCalledExactlyOnceWith({ location: 'Plovdiv' });
        expect(onSaved).toHaveBeenCalledTimes(2);
    });

    it('should_not_upload_when_the_text_is_refused', async () => {
        vi.mocked(updateProfile)
            .mockRejectedValue(new ApiError(400, ['bio must be at most 160 characters']));
        const { onClose } = renderSheet();
        await replaceText(bioField(), 'New bio');
        await user.upload(photoInput(), photoFile());

        await user.click(saveButton());
        await advance(1);

        expect(uploadProfilePicture).not.toHaveBeenCalled();
        expect(onClose).not.toHaveBeenCalled();
    });

    it('should_keep_the_sheet_open_with_the_photo_error_when_the_text_saved_and_the_photo_failed', async () => {
        vi.mocked(uploadProfilePicture)
            .mockRejectedValueOnce(new ApiError(415, ['Unsupported image type.']));
        const onClose = vi.fn();
        render(<SheetInPage onClose={onClose} />);
        await replaceText(locationField(), 'Plovdiv');
        await user.upload(photoInput(), photoFile());

        await user.click(saveButton());
        await advance(1);

        expect(onClose).not.toHaveBeenCalled();
        expect(alertTexts()).toContain('UNSUPPORTED IMAGE TYPE');
        expect(locationField().value).toBe('Plovdiv');
        expect(saveButton().textContent).toBe('SAVE CHANGES');
    });

    it('should_send_only_the_photo_on_the_retry_because_the_text_counts_as_saved', async () => {
        vi.mocked(updateProfile)
            .mockResolvedValue(userProfile({
                ...PROFILE,
                location: 'Plovdiv',
            }));
        vi.mocked(uploadProfilePicture)
            .mockRejectedValueOnce(new ApiError(502, []));
        const onClose = vi.fn();
        render(<SheetInPage onClose={onClose} />);
        await replaceText(locationField(), 'Plovdiv');
        await user.upload(photoInput(), photoFile());
        await user.click(saveButton());
        await advance(1);

        await user.click(saveButton());
        await advance(1);

        expect(updateProfile).toHaveBeenCalledTimes(1);
        expect(uploadProfilePicture).toHaveBeenCalledTimes(2);
        expect(onClose).toHaveBeenCalledOnce();
    });

    it('should_clear_the_photo_error_when_another_photo_is_picked', async () => {
        vi.mocked(uploadProfilePicture)
            .mockRejectedValueOnce(new ApiError(502, []));
        renderSheet();
        await user.upload(photoInput(), photoFile('first.png'));
        await user.click(saveButton());
        await advance(1);
        expect(alertTexts()).toContain('SIGNAL LOST — TRY AGAIN');

        await user.upload(photoInput(), photoFile('second.png'));

        expect(areAlertsEmpty()).toBe(true);
    });
});
