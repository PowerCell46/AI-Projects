import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthApiError, logout, me } from '../../api/auth';
import { ROUTES } from '../../routes';
import { renderApp } from '../../test/renderApp';
import { advance } from '../../test/stepFlowHelpers';
import { SIGNAL_LOST_MESSAGE } from '../../utils/authErrors';


vi.mock('../../api/auth', async (importOriginal) => ({
    ...await importOriginal<typeof import('../../api/auth')>(),
    me: vi.fn(),
    logout: vi.fn(),
}));

const SIGNED_IN_USER = { id: 'user-1', username: 'peter_g', email: 'peter@example.com' };

let user: ReturnType<typeof userEvent.setup>;

beforeEach(async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'requestAnimationFrame', 'cancelAnimationFrame'] });
    user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    vi.mocked(me).mockResolvedValue(SIGNED_IN_USER);
    vi.mocked(logout).mockReset();

    await renderApp(ROUTES.feed);
});

afterEach(() => {
    vi.useRealTimers();
});

describe('the feed placeholder', () => {
    it('should_show_the_username_from_the_session', () => {
        expect(screen.getByRole('heading', { name: '@peter_g' })).toBeTruthy();
    });

    it('should_call_logout_and_land_on_login_when_log_out_is_clicked', async () => {
        vi.mocked(logout).mockResolvedValue(undefined);
        vi.mocked(me).mockRejectedValue(new AuthApiError(401, []));

        await user.click(screen.getByRole('button', { name: 'LOG OUT' }));
        await advance(1);

        expect(logout).toHaveBeenCalledTimes(1);
        expect(window.location.pathname).toBe(ROUTES.login);
    });

    it('should_stay_on_the_feed_with_signal_lost_when_logout_fails', async () => {
        vi.mocked(logout).mockRejectedValue(new AuthApiError(0, []));

        await user.click(screen.getByRole('button', { name: 'LOG OUT' }));
        await advance(1);

        expect(window.location.pathname).toBe(ROUTES.feed);
        expect(screen.getByRole('alert').textContent).toBe(SIGNAL_LOST_MESSAGE);
    });
});
