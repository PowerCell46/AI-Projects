import { act, render, screen } from '@testing-library/react';
import { StrictMode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { confirm } from '../../api/auth';
import { ApiError } from '../../api/http';
import { ROUTES } from '../../routes';
import { expectedTransform, horizonTransform } from '../../test/stepFlowHelpers';
import ConfirmPage from './ConfirmPage';


vi.mock('../../api/auth', async (importOriginal) => ({
    ...await importOriginal<typeof import('../../api/auth')>(),
    confirm: vi.fn(),
}));

async function renderConfirm(search: string) {
    await act(async () => {
        render(
            <StrictMode>
                <MemoryRouter initialEntries={[`${ROUTES.confirm}${search}`]}>
                    <ConfirmPage />
                </MemoryRouter>
            </StrictMode>,
        );
    });
}

beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'requestAnimationFrame', 'cancelAnimationFrame'] });
    vi.mocked(confirm).mockReset();
});

afterEach(() => {
    vi.useRealTimers();
});

describe('a valid link', () => {
    it('should_confirm_once_with_the_token_even_under_strict_mode', async () => {
        vi.mocked(confirm).mockResolvedValue(undefined);

        await renderConfirm('?token=abc123');

        expect(confirm).toHaveBeenCalledTimes(1);
        expect(confirm).toHaveBeenCalledWith('abc123');
    });

    it('should_show_the_cleared_state_with_a_login_button_when_the_server_answers_204', async () => {
        vi.mocked(confirm).mockResolvedValue(undefined);

        await renderConfirm('?token=abc123');

        expect(screen.getByText('ACCOUNT CONFIRMED')).toBeTruthy();
        expect(screen.getByRole('heading', { name: "You're cleared to descend." })).toBeTruthy();
        expect(screen.getByRole('link', { name: 'LOG IN' }).getAttribute('href')).toBe(ROUTES.login);
        expect(horizonTransform()).toBe(expectedTransform(4900, 4900));
    });

    it('should_draw_the_loader_rule_while_the_request_is_pending', async () => {
        vi.mocked(confirm).mockReturnValue(new Promise(() => {}));

        await renderConfirm('?token=abc123');

        expect(document.querySelector('.arrival-rule')).not.toBeNull();
        expect(screen.queryByRole('link')).toBeNull();
    });
});

describe('a rejected link', () => {
    it('should_show_the_expired_alarm_with_a_resend_link_when_the_server_answers_400', async () => {
        vi.mocked(confirm).mockRejectedValue(new ApiError(400, ['Invalid or expired token.']));

        await renderConfirm('?token=abc123');

        expect(screen.getByText('LINK EXPIRED OR ALREADY USED')).toBeTruthy();
        expect(document.querySelector('.descent-stage')?.getAttribute('data-alarm')).toBe('true');
        expect(screen.getByRole('link', { name: 'RESEND LINK' }).getAttribute('href')).toBe(ROUTES.resend);
        expect(screen.getByRole('link', { name: 'LOG IN' }).getAttribute('href')).toBe(ROUTES.login);
    });

    it('should_show_the_expired_alarm_without_calling_the_server_when_there_is_no_token', async () => {
        await renderConfirm('');

        expect(confirm).not.toHaveBeenCalled();
        expect(screen.getByText('LINK EXPIRED OR ALREADY USED')).toBeTruthy();
    });
});

describe('a failed request', () => {
    it('should_show_signal_lost_and_confirm_again_when_try_again_is_clicked', async () => {
        vi.mocked(confirm).mockRejectedValueOnce(new ApiError(0, []));
        await renderConfirm('?token=abc123');
        vi.mocked(confirm).mockResolvedValue(undefined);

        expect(screen.getByText('SIGNAL LOST — TRY AGAIN')).toBeTruthy();

        await act(async () => {
            screen.getByRole('button', { name: 'TRY AGAIN' }).click();
        });

        expect(confirm).toHaveBeenCalledTimes(2);
        expect(screen.getByText('ACCOUNT CONFIRMED')).toBeTruthy();
    });
});
