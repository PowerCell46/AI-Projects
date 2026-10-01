import { screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthApiError, me } from './api/auth';
import { ROUTES } from './routes';
import { renderApp } from './test/renderApp';


vi.mock('./api/auth', async (importOriginal) => ({
    ...await importOriginal<typeof import('./api/auth')>(),
    me: vi.fn(),
}));

const SIGNED_IN_USER = { id: 'user-1', username: 'peter_g', email: 'peter@example.com' };

beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'requestAnimationFrame', 'cancelAnimationFrame'] });
});

afterEach(() => {
    vi.useRealTimers();
});

describe('a logged-out visitor', () => {
    beforeEach(() => {
        vi.mocked(me).mockRejectedValue(new AuthApiError(401, []));
    });

    it('should_see_the_login_question_when_the_path_is_login', async () => {
        await renderApp(ROUTES.login);

        expect(screen.getByRole('heading', { name: 'Who are you out here?' })).toBeTruthy();
    });

    it('should_be_sent_from_the_feed_to_login', async () => {
        await renderApp(ROUTES.feed);

        expect(window.location.pathname).toBe(ROUTES.login);
    });

    it('should_be_sent_from_the_root_to_login', async () => {
        await renderApp('/');

        expect(window.location.pathname).toBe(ROUTES.login);
    });

    it('should_be_sent_from_an_unknown_path_to_login', async () => {
        await renderApp('/nowhere');

        expect(window.location.pathname).toBe(ROUTES.login);
    });

    it('should_be_able_to_open_the_register_page', async () => {
        await renderApp(ROUTES.register);

        expect(screen.getByRole('heading', { name: 'Where do we reach you?' })).toBeTruthy();
    });
});

describe('a logged-in user', () => {
    beforeEach(() => {
        vi.mocked(me).mockResolvedValue(SIGNED_IN_USER);
    });

    it('should_be_sent_from_login_to_the_feed', async () => {
        await renderApp(ROUTES.login);

        expect(window.location.pathname).toBe(ROUTES.feed);
    });

    it('should_be_sent_from_register_to_the_feed', async () => {
        await renderApp(ROUTES.register);

        expect(window.location.pathname).toBe(ROUTES.feed);
    });

    it('should_be_sent_from_the_root_to_the_feed', async () => {
        await renderApp('/');

        expect(window.location.pathname).toBe(ROUTES.feed);
    });

    it('should_stay_on_the_feed', async () => {
        await renderApp(ROUTES.feed);

        expect(window.location.pathname).toBe(ROUTES.feed);
    });
});
