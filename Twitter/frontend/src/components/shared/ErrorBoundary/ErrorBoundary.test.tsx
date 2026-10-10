import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ErrorBoundary from './ErrorBoundary';


function BrokenPage(): never {
    throw new Error('The page failed to render.');
}

describe('ErrorBoundary', () => {
    beforeEach(() => {
        // React logs the error it catches; keep the test output clean
        vi.spyOn(console, 'error').mockImplementation(() => {});
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should_show_its_children_when_nothing_throws', () => {
        render(
            <ErrorBoundary>
                <p>Healthy page</p>
            </ErrorBoundary>,
        );

        expect(screen.getByText('Healthy page')).toBeTruthy();
        expect(screen.queryByRole('alert')).toBeNull();
    });

    it('should_show_the_fallback_when_a_child_throws_while_rendering', () => {
        render(
            <ErrorBoundary>
                <BrokenPage />
            </ErrorBoundary>,
        );

        expect(screen.getByRole('alert').textContent).toContain('SIGNAL LOST');
        expect(screen.getByRole('button', { name: 'RELOAD' })).toBeTruthy();
    });

    it('should_reload_the_page_when_reload_is_clicked', () => {
        const reload = vi.fn();
        vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, reload });
        render(
            <ErrorBoundary>
                <BrokenPage />
            </ErrorBoundary>,
        );

        fireEvent.click(screen.getByRole('button', { name: 'RELOAD' }));

        expect(reload).toHaveBeenCalledOnce();
    });
});
