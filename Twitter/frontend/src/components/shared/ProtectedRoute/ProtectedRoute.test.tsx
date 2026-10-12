import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { useAuth } from '../../../contexts/AuthContext';
import type { SessionStatus } from '../../../contexts/AuthContext';
import { ROUTES } from '../../../routes';
import ProtectedRoute from './ProtectedRoute';


vi.mock('../../../contexts/AuthContext', () => ({
    useAuth: vi.fn(),
}));

function signInAs(status: SessionStatus) {
    vi.mocked(useAuth).mockReturnValue({
        status,
        user: null,
        isSigningOut: false,
        hasSignedOut: false,
        signIn: vi.fn(),
        signOut: vi.fn(),
    });
}

function renderGuard() {
    return render(
        <MemoryRouter initialEntries={[ROUTES.feed]}>
            <Routes>
                <Route element={<ProtectedRoute />}>
                    <Route path={ROUTES.feed} element={<p>feed page</p>} />
                </Route>
                <Route path={ROUTES.login} element={<p>login page</p>} />
            </Routes>
        </MemoryRouter>,
    );
}

describe('the route guard', () => {
    it('should_show_a_loading_indicator_while_the_session_is_checked', () => {
        signInAs('loading');

        renderGuard();

        expect(screen.getByRole('status').textContent).toBe('Loading…');
    });

    it('should_show_the_page_when_signed_in', () => {
        signInAs('authenticated');

        renderGuard();

        expect(screen.getByText('feed page')).toBeTruthy();
    });

    it('should_send_an_anonymous_visitor_to_login', () => {
        signInAs('anonymous');

        renderGuard();

        expect(screen.getByText('login page')).toBeTruthy();
    });
});
