import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { me } from '../../src/api/accounts/auth';
import { fetchFeed } from '../../src/api/tweets/feed';
import { AuthProvider, useAuth } from '../../src/contexts/AuthContext';
import { installFetchStub, respondWith } from '../support/fetchStub';


vi.mock('../../src/api/accounts/auth', async (importOriginal) => ({
    ...await importOriginal<typeof import('../../src/api/accounts/auth')>(),
    me: vi.fn(),
}));

const SIGNED_IN_USER = {
    id: 'user-1',
    username: 'peter_g',
    email: 'peter@example.com',
};

installFetchStub();

function SessionProbe() {
    const { status } = useAuth();

    async function handleFeedRequest() {
        await fetchFeed({
            cursor: null,
            size: 20,
        })
            .catch(() => undefined);
    }

    return (
        <>
            <p data-testid="status">{status}</p>
            <button type="button" onClick={handleFeedRequest}>request the feed</button>
        </>
    );
}

async function renderProbe() {
    await act(async () => {
        render(
            <AuthProvider>
                <SessionProbe />
            </AuthProvider>,
        );
    });
}

beforeEach(() => {
    vi.mocked(me).mockResolvedValue(SIGNED_IN_USER);
});

describe('session expiry', () => {
    it('should_end_the_session_when_a_non_auth_request_answers_401', async () => {
        await renderProbe();
        respondWith(401);

        await userEvent.click(screen.getByRole('button', { name: 'request the feed' }));

        expect(screen.getByTestId('status').textContent).toBe('anonymous');
    });

    it('should_keep_the_session_when_a_non_auth_request_answers_500', async () => {
        await renderProbe();
        respondWith(500);

        await userEvent.click(screen.getByRole('button', { name: 'request the feed' }));

        expect(screen.getByTestId('status').textContent).toBe('authenticated');
    });
});
