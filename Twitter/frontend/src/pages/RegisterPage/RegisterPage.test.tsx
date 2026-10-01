import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthApiError, me, register, resendConfirmation } from '../../api/auth';
import { RESEND_COOLDOWN_MS } from '../../components/shared/ResendButton/ResendButton';
import { ROUTES } from '../../routes';
import { renderApp } from '../../test/renderApp';
import {
    advance,
    expectedTransform,
    horizonTransform,
    settleTransition,
    waitForHistoryTraversal,
} from '../../test/stepFlowHelpers';
import { SIGNAL_LOST_MESSAGE } from '../../utils/authErrors';


vi.mock('../../api/auth', async (importOriginal) => ({
    ...await importOriginal<typeof import('../../api/auth')>(),
    register: vi.fn(),
    resendConfirmation: vi.fn(),
    me: vi.fn(),
}));

const SEAFLOOR_DEPTH_METRES = 10910;

const REGISTERED_USER = { id: 'user-1', username: 'peter_g', email: 'peter@example.com' };

let user: ReturnType<typeof userEvent.setup>;

async function submitRegistration() {
    await user.type(screen.getByRole('textbox', { name: 'email address' }), 'peter@example.com{Enter}');
    await settleTransition();
    await user.type(screen.getByRole('textbox', { name: 'username' }), 'peter_g{Enter}');
    await settleTransition();
    await user.type(screen.getByLabelText('password'), 'Secret123{Enter}');
    await advance(1);
}

async function settleAfterJumpBack() {
    await waitForHistoryTraversal();
    await settleTransition();
}

beforeEach(async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'requestAnimationFrame', 'cancelAnimationFrame'] });
    user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    vi.mocked(me).mockRejectedValue(new AuthApiError(401, []));
    vi.mocked(register).mockReset();
    vi.mocked(resendConfirmation).mockReset();

    await renderApp(ROUTES.register);
});

afterEach(() => {
    vi.useRealTimers();
});

describe('conflicts', () => {
    it('should_travel_back_to_the_email_step_when_the_email_is_already_registered', async () => {
        vi.mocked(register).mockRejectedValue(new AuthApiError(409, ['Email already registered.']));

        await submitRegistration();
        await settleAfterJumpBack();

        expect(screen.getByRole('heading', { name: 'Where do we reach you?' })).toBeTruthy();
        expect(screen.getByRole('alert').textContent).toBe('EMAIL ALREADY REGISTERED');
        expect(horizonTransform()).toBe(expectedTransform(140, SEAFLOOR_DEPTH_METRES));
    });

    it('should_travel_back_to_the_username_step_when_the_username_is_taken', async () => {
        vi.mocked(register).mockRejectedValue(new AuthApiError(409, ['Username already taken.']));

        await submitRegistration();
        await settleAfterJumpBack();

        expect(screen.getByRole('heading', { name: "Pick the name you'll be known by." })).toBeTruthy();
        expect(screen.getByRole('alert').textContent).toBe('USERNAME ALREADY TAKEN');
        expect(horizonTransform()).toBe(expectedTransform(3860, SEAFLOOR_DEPTH_METRES));
    });

    it('should_keep_every_typed_value_after_jumping_back', async () => {
        vi.mocked(register).mockRejectedValue(new AuthApiError(409, ['Email already registered.']));

        await submitRegistration();
        await settleAfterJumpBack();

        expect(screen.getByRole<HTMLInputElement>('textbox', { name: 'email address' }).value)
            .toBe('peter@example.com');
        expect(screen.getByLabelText<HTMLInputElement>('password').value).toBe('Secret123');
    });
});

describe('validation failures from the server', () => {
    it('should_travel_to_the_earliest_failing_field_when_the_server_answers_400', async () => {
        vi.mocked(register).mockRejectedValue(new AuthApiError(400, [
            'password must contain a lowercase letter, an uppercase letter and a digit',
            'email must be a valid email address',
        ]));

        await submitRegistration();
        await settleAfterJumpBack();

        expect(screen.getByRole('heading', { name: 'Where do we reach you?' })).toBeTruthy();
        expect(screen.getByRole('alert').textContent).toBe('EMAIL MUST BE A VALID EMAIL ADDRESS');
    });

    it('should_stay_on_the_password_step_when_only_the_password_fails', async () => {
        vi.mocked(register).mockRejectedValue(new AuthApiError(400, ['password must be 8 to 72 characters']));

        await submitRegistration();
        await settleTransition();

        expect(screen.getByRole('alert').textContent).toBe('PASSWORD MUST BE 8 TO 72 CHARACTERS');
        expect(horizonTransform()).toBe(expectedTransform(9720, SEAFLOOR_DEPTH_METRES));
    });
});

describe('signal lost', () => {
    it('should_hold_at_the_password_step_when_the_server_answers_500', async () => {
        vi.mocked(register).mockRejectedValue(new AuthApiError(500, []));

        await submitRegistration();

        expect(screen.getByRole('alert').textContent).toBe(SIGNAL_LOST_MESSAGE);
        expect(horizonTransform()).toBe(expectedTransform(9720, SEAFLOOR_DEPTH_METRES));
    });
});

describe('arrival', () => {
    it('should_show_the_registered_email_without_redirecting', async () => {
        vi.mocked(register).mockResolvedValue(REGISTERED_USER);

        await submitRegistration();
        await advance(5000);

        expect(register).toHaveBeenCalledWith({
            email: 'peter@example.com',
            username: 'peter_g',
            password: 'Secret123',
        });
        expect(screen.getByRole('heading', { name: 'Check your inbox.' })).toBeTruthy();
        expect(screen.getByText('CONFIRMATION SENT TO PETER@EXAMPLE.COM')).toBeTruthy();
        expect(window.location.pathname).toBe(ROUTES.register);
        expect(horizonTransform()).toBe(expectedTransform(SEAFLOOR_DEPTH_METRES, SEAFLOOR_DEPTH_METRES));
    });

    it('should_link_to_login_from_the_arrival_footer', async () => {
        vi.mocked(register).mockResolvedValue(REGISTERED_USER);

        await submitRegistration();

        expect(screen.getByRole('link', { name: 'LOG IN' }).getAttribute('href')).toBe(ROUTES.login);
    });
});

describe('resend', () => {
    async function arriveAndResend() {
        vi.mocked(register).mockResolvedValue(REGISTERED_USER);
        vi.mocked(resendConfirmation).mockResolvedValue(undefined);
        await submitRegistration();
        await user.click(screen.getByRole('button', { name: 'RESEND' }));
        await advance(1);
    }

    it('should_send_a_new_link_and_read_link_sent_when_resend_is_clicked', async () => {
        await arriveAndResend();

        const sentButton = screen.getByRole('button', { name: 'LINK SENT' });

        expect(resendConfirmation).toHaveBeenCalledWith('peter@example.com');
        expect(sentButton.getAttribute('aria-disabled')).toBe('true');
    });

    it('should_not_send_again_when_clicked_during_the_cooldown', async () => {
        await arriveAndResend();

        await user.click(screen.getByRole('button', { name: 'LINK SENT' }));

        expect(resendConfirmation).toHaveBeenCalledTimes(1);
    });

    it('should_enable_the_button_again_after_60_seconds', async () => {
        await arriveAndResend();

        await advance(RESEND_COOLDOWN_MS - 1);

        expect(screen.getByRole('button', { name: 'LINK SENT' })).toBeTruthy();

        await advance(1);

        expect(screen.getByRole('button', { name: 'RESEND' }).getAttribute('aria-disabled')).toBe('false');
    });

    it('should_show_signal_lost_on_the_button_and_allow_a_retry_when_the_send_fails', async () => {
        vi.mocked(register).mockResolvedValue(REGISTERED_USER);
        vi.mocked(resendConfirmation).mockRejectedValue(new AuthApiError(0, []));
        await submitRegistration();

        await user.click(screen.getByRole('button', { name: 'RESEND' }));
        await advance(1);

        expect(screen.getByRole('button', { name: SIGNAL_LOST_MESSAGE }).getAttribute('aria-disabled')).toBe('false');
    });
});
