import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthApiError, me, resendConfirmation } from '../../api/auth';
import { RESEND_COOLDOWN_MS } from '../../components/shared/ResendButton/ResendButton';
import { ROUTES } from '../../routes';
import { renderApp } from '../../test/renderApp';
import { advance } from '../../test/stepFlowHelpers';
import { SIGNAL_LOST_MESSAGE } from '../../utils/authErrors';
import { EMAIL_MESSAGE, EMPTY_FIELD_MESSAGE } from '../../utils/validation';


vi.mock('../../api/auth', async (importOriginal) => ({
    ...await importOriginal<typeof import('../../api/auth')>(),
    me: vi.fn(),
    resendConfirmation: vi.fn(),
}));

let user: ReturnType<typeof userEvent.setup>;

async function renderResend(locationState: object | null = null) {
    await renderApp(ROUTES.resend, locationState);
}

beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'requestAnimationFrame', 'cancelAnimationFrame'] });
    user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    vi.mocked(me).mockRejectedValue(new AuthApiError(401, []));
    vi.mocked(resendConfirmation).mockReset();
});

afterEach(() => {
    vi.useRealTimers();
});

describe('the form', () => {
    it('should_ask_where_to_send_the_link_with_an_empty_input', async () => {
        await renderResend();

        expect(screen.getByRole('heading', { name: 'Where should we send it?' })).toBeTruthy();
        expect(screen.getByRole<HTMLInputElement>('textbox', { name: 'email address' }).value).toBe('');
    });

    it('should_prefill_the_email_from_the_router_state', async () => {
        await renderResend({ email: 'peter@example.com' });

        expect(screen.getByRole<HTMLInputElement>('textbox', { name: 'email address' }).value)
            .toBe('peter@example.com');
    });

    it('should_hold_with_the_empty_message_when_the_field_is_empty', async () => {
        await renderResend();

        await user.click(screen.getByRole('button', { name: 'SEND LINK' }));

        expect(screen.getByRole('alert').textContent).toBe(EMPTY_FIELD_MESSAGE);
        expect(resendConfirmation).not.toHaveBeenCalled();
    });

    it('should_hold_with_the_email_message_when_the_format_is_wrong', async () => {
        await renderResend();

        await user.type(screen.getByRole('textbox', { name: 'email address' }), 'not-an-email{Enter}');

        expect(screen.getByRole('alert').textContent).toBe(EMAIL_MESSAGE);
        expect(resendConfirmation).not.toHaveBeenCalled();
    });
});

describe('sending', () => {
    it('should_show_the_dispatched_arrival_with_the_button_cooling_down_when_the_server_answers', async () => {
        vi.mocked(resendConfirmation).mockResolvedValue(undefined);
        await renderResend({ email: 'peter@example.com' });

        await user.click(screen.getByRole('button', { name: 'SEND LINK' }));
        await advance(1);

        expect(resendConfirmation).toHaveBeenCalledWith('peter@example.com');
        expect(screen.getByText('LINK DISPATCHED')).toBeTruthy();
        expect(screen.getByText('IF AN ACCOUNT IS WAITING, A NEW LINK IS ON ITS WAY')).toBeTruthy();
        expect(screen.getByRole('button', { name: 'LINK SENT' }).getAttribute('aria-disabled')).toBe('true');
        expect(screen.getByRole('link', { name: 'LOG IN' }).getAttribute('href')).toBe(ROUTES.login);
    });

    it('should_allow_another_send_after_60_seconds', async () => {
        vi.mocked(resendConfirmation).mockResolvedValue(undefined);
        await renderResend({ email: 'peter@example.com' });
        await user.click(screen.getByRole('button', { name: 'SEND LINK' }));
        await advance(1);

        await advance(RESEND_COOLDOWN_MS);

        expect(screen.getByRole('button', { name: 'RESEND' }).getAttribute('aria-disabled')).toBe('false');
    });

    it('should_show_signal_lost_and_stay_on_the_form_when_the_network_fails', async () => {
        vi.mocked(resendConfirmation).mockRejectedValue(new AuthApiError(0, []));
        await renderResend({ email: 'peter@example.com' });

        await user.click(screen.getByRole('button', { name: 'SEND LINK' }));
        await advance(1);

        expect(screen.getByRole('alert').textContent).toBe(SIGNAL_LOST_MESSAGE);
        expect(screen.queryByText('LINK DISPATCHED')).toBeNull();
    });
});
