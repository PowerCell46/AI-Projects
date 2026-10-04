import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { login, me } from '../../api/auth';
import { ApiError } from '../../api/http';
import { renderApp } from '../../test/renderApp';
import {
    advance,
    expectedTransform,
    horizonTransform,
    settleTransition,
} from '../../test/stepFlowHelpers';
import {
    CONFIRM_EMAIL_MESSAGE,
    INVALID_CREDENTIALS_MESSAGE,
    RISE_METRES,
    SIGNAL_LOST_MESSAGE,
} from '../../utils/authErrors';
import { ROUTES } from '../../routes';


vi.mock('../../api/auth', async (importOriginal) => ({
    ...await importOriginal<typeof import('../../api/auth')>(),
    login: vi.fn(),
    me: vi.fn(),
}));

const ARRIVAL_RULE_DRAW_MS = 1450;

const PASSWORD_STEP_DEPTH_METRES = 3860;

const SEAFLOOR_DEPTH_METRES = 4900;

const SIGNED_IN_USER = {
    id: 'user-1',
    username: 'peter_g',
    email: 'peter@example.com',
};

let user: ReturnType<typeof userEvent.setup>;

async function submitCredentials(identifier: string, password: string) {
    await user.type(
        screen.getByRole('textbox', { name: 'email or username' }),
        `${identifier}{Enter}`,
    );
    await settleTransition();
    await user.type(screen.getByLabelText('password'), `${password}{Enter}`);
    await advance(1);
}

function isAlarmShown(): boolean {
    return document.querySelector('.descent-stage')?.getAttribute('data-alarm') === 'true';
}

beforeEach(async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'requestAnimationFrame', 'cancelAnimationFrame'] });
    user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    vi.mocked(me).mockRejectedValue(new ApiError(401, []));
    vi.mocked(login).mockReset();

    await renderApp(ROUTES.login);
});

afterEach(() => {
    vi.useRealTimers();
});

describe('rejected credentials', () => {
    it('should_rise_420_metres_in_alarm_colours_when_the_server_answers_401', async () => {
        vi.mocked(login).mockRejectedValue(new ApiError(401, ['Invalid credentials.']));

        await submitCredentials('peter_g', 'wrong');

        expect(screen.getByRole('alert').textContent).toBe(INVALID_CREDENTIALS_MESSAGE);
        expect(isAlarmShown()).toBe(true);
        expect(horizonTransform()).toBe(
            expectedTransform(PASSWORD_STEP_DEPTH_METRES - RISE_METRES, SEAFLOOR_DEPTH_METRES),
        );
    });

    it('should_return_to_the_true_depth_and_normal_colours_when_a_key_is_typed_after_a_401', async () => {
        vi.mocked(login).mockRejectedValue(new ApiError(401, []));
        await submitCredentials('peter_g', 'wrong');

        await user.type(screen.getByLabelText('password'), 'x');

        expect(isAlarmShown()).toBe(false);
        expect(screen.getByRole('alert').textContent).toBe('');
        expect(horizonTransform()).toBe(expectedTransform(PASSWORD_STEP_DEPTH_METRES, SEAFLOOR_DEPTH_METRES));
    });

    it('should_keep_what_was_typed_when_the_server_answers_401', async () => {
        vi.mocked(login).mockRejectedValue(new ApiError(401, []));

        await submitCredentials('peter_g', 'wrong');

        expect(screen.getByLabelText<HTMLInputElement>('password').value).toBe('wrong');
    });
});

describe('unconfirmed account', () => {
    it('should_hold_the_gauge_and_offer_a_resend_link_when_the_server_answers_403', async () => {
        vi.mocked(login).mockRejectedValue(new ApiError(403, ['Email is not confirmed.']));

        await submitCredentials('peter@example.com', 'secret');

        expect(screen.getByRole('alert').textContent).toBe(CONFIRM_EMAIL_MESSAGE);
        expect(isAlarmShown()).toBe(false);
        expect(horizonTransform()).toBe(expectedTransform(PASSWORD_STEP_DEPTH_METRES, SEAFLOOR_DEPTH_METRES));
        expect(screen.getByRole('link', { name: 'RESEND LINK' }).getAttribute('href')).toBe(ROUTES.resend);
    });

    it('should_pass_the_email_to_the_resend_page_when_the_identifier_is_an_email', async () => {
        vi.mocked(login).mockRejectedValue(new ApiError(403, []));
        await submitCredentials('peter@example.com', 'secret');

        await user.click(screen.getByRole('link', { name: 'RESEND LINK' }));

        expect(window.location.pathname).toBe(ROUTES.resend);
        expect(window.history.state.usr).toEqual({ email: 'peter@example.com' });
    });

    it('should_pass_no_email_to_the_resend_page_when_the_identifier_is_a_username', async () => {
        vi.mocked(login).mockRejectedValue(new ApiError(403, []));
        await submitCredentials('peter_g', 'secret');

        await user.click(screen.getByRole('link', { name: 'RESEND LINK' }));

        expect(window.history.state.usr).toBeNull();
    });
});

describe('signal lost', () => {
    it('should_hold_at_the_password_step_when_the_server_answers_500', async () => {
        vi.mocked(login).mockRejectedValue(new ApiError(500, []));

        await submitCredentials('peter_g', 'secret');

        expect(screen.getByRole('alert').textContent).toBe(SIGNAL_LOST_MESSAGE);
        expect(isAlarmShown()).toBe(false);
        expect(screen.queryByRole('link', { name: 'RESEND LINK' })).toBeNull();
    });

    it('should_hold_at_the_password_step_when_the_network_fails', async () => {
        vi.mocked(login).mockRejectedValue(new ApiError(0, []));

        await submitCredentials('peter_g', 'secret');

        expect(screen.getByRole('alert').textContent).toBe(SIGNAL_LOST_MESSAGE);
    });

    it('should_try_again_when_enter_is_pressed_after_signal_lost', async () => {
        vi.mocked(login).mockRejectedValueOnce(new ApiError(500, []));
        await submitCredentials('peter_g', 'secret');
        vi.mocked(login).mockResolvedValue(SIGNED_IN_USER);

        await user.type(screen.getByLabelText('password'), '{Enter}');
        await advance(1);

        expect(login).toHaveBeenCalledTimes(2);
        expect(screen.getByRole('heading', { name: /Seafloor reached/ })).toBeTruthy();
    });
});

describe('arrival', () => {
    it('should_send_the_typed_values_and_show_the_arrival_without_leaving_the_page', async () => {
        vi.mocked(login).mockResolvedValue(SIGNED_IN_USER);

        await submitCredentials('peter_g', 'secret');

        expect(login).toHaveBeenCalledWith({
            identifier: 'peter_g',
            password: 'secret',
        });
        expect(screen.getByRole('heading', { name: /Seafloor reached/ })).toBeTruthy();
        expect(screen.getByText('IDENTITY CONFIRMED')).toBeTruthy();
        expect(window.location.pathname).toBe(ROUTES.login);
        expect(horizonTransform()).toBe(expectedTransform(SEAFLOOR_DEPTH_METRES, SEAFLOOR_DEPTH_METRES));
    });

    it('should_go_to_the_feed_when_the_arrival_rule_has_been_drawn', async () => {
        vi.mocked(login).mockResolvedValue(SIGNED_IN_USER);
        await submitCredentials('peter_g', 'secret');

        await advance(ARRIVAL_RULE_DRAW_MS - 1);

        expect(window.location.pathname).toBe(ROUTES.login);

        await advance(1);

        expect(window.location.pathname).toBe(ROUTES.feed);
    });
});

describe('footer', () => {
    it('should_link_to_the_register_page', () => {
        expect(screen.getByRole('link', { name: 'CREATE ACCOUNT' }).getAttribute('href')).toBe(ROUTES.register);
    });
});

describe('double submission', () => {
    it('should_send_one_request_when_enter_is_pressed_twice_while_waiting', async () => {
        vi.mocked(login).mockReturnValue(new Promise(() => {}));
        await user.type(
            screen.getByRole('textbox', { name: 'email or username' }),
            'peter_g{Enter}',
        );
        await settleTransition();

        await act(async () => {
            await user.type(screen.getByLabelText('password'), 'secret{Enter}{Enter}');
        });

        expect(login).toHaveBeenCalledTimes(1);
    });
});
