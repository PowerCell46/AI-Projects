import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DatePicker } from './DatePicker';


describe('DatePicker', () => {
    it('shows the placeholder when empty', () => {
        render(<DatePicker value="" onChange={vi.fn()} ariaLabel="Date" />);

        expect(screen.getByRole('button', { name: 'Date' })).toHaveTextContent('dd/mm/yyyy');
    });

    it('shows the value as dd/mm/yyyy', () => {
        render(<DatePicker value="2026-06-10" onChange={vi.fn()} ariaLabel="Date" />);

        expect(screen.getByRole('button', { name: 'Date' })).toHaveTextContent('10/06/2026');
    });

    it('opens to the value’s month and emits the ISO date of the clicked day', async () => {
        const onChange = vi.fn();
        render(<DatePicker value="2026-06-10" onChange={onChange} ariaLabel="Date" />);

        await userEvent.click(screen.getByRole('button', { name: 'Date' }));
        expect(screen.getByText('June 2026')).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: '5' }));

        expect(onChange).toHaveBeenCalledWith('2026-06-05');
    });

    it('disables days after max', async () => {
        render(<DatePicker value="2026-06-10" onChange={vi.fn()} ariaLabel="Date" max="2026-06-10" />);

        await userEvent.click(screen.getByRole('button', { name: 'Date' }));

        expect(screen.getByRole('button', { name: '20' })).toBeDisabled();
        expect(screen.getByRole('button', { name: '9' })).toBeEnabled();
    });

    it('navigates to the previous month', async () => {
        render(<DatePicker value="2026-06-10" onChange={vi.fn()} ariaLabel="Date" />);

        await userEvent.click(screen.getByRole('button', { name: 'Date' }));
        await userEvent.click(screen.getByRole('button', { name: 'Previous month' }));

        expect(screen.getByText('May 2026')).toBeInTheDocument();
    });

    it('clears the value', async () => {
        const onChange = vi.fn();
        render(<DatePicker value="2026-06-10" onChange={onChange} ariaLabel="Date" />);

        await userEvent.click(screen.getByRole('button', { name: 'Date' }));
        await userEvent.click(screen.getByRole('button', { name: 'clear' }));

        expect(onChange).toHaveBeenCalledWith('');
    });
});
