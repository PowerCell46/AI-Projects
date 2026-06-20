import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HoldingsTable } from './HoldingsTable';
import type { Holding } from '../../types/holding';
import type { PageResponse } from '../../types/page';


const holding = (overrides: Partial<Holding> = {}): Holding => ({
    id: 1,
    assetId: 7,
    name: 'Apple',
    boughtForAmount: 500,
    quantity: 2,
    price: 250,
    date: '2026-03-01',
    note: null,
    createdAt: '2026-03-01T10:00:00Z',
    ...overrides,
});

const page = (overrides: Partial<PageResponse<Holding>> = {}): PageResponse<Holding> => ({
    content: [holding()],
    page: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
    ...overrides,
});

const noop = () => { };


describe('HoldingsTable', () => {
    it('shows an empty state when there are no holdings', () => {
        render(<HoldingsTable page={page({ content: [], totalElements: 0 })} onEdit={noop} onDelete={noop} onPageChange={noop} />);

        expect(screen.getByText(/no holdings yet/i)).toBeInTheDocument();
    });

    it('renders a row with the formatted figures', () => {
        render(<HoldingsTable page={page()} onEdit={noop} onDelete={noop} onPageChange={noop} />);

        expect(screen.getByText('Apple')).toBeInTheDocument();
        expect(screen.getByText('2026-03-01')).toBeInTheDocument();
        expect(screen.getByText('500.00')).toBeInTheDocument();
    });

    it('requires a second click to confirm a delete', async () => {
        const onDelete = vi.fn();
        render(<HoldingsTable page={page()} onEdit={noop} onDelete={onDelete} onPageChange={noop} />);

        await userEvent.click(screen.getByRole('button', { name: 'delete' }));
        expect(onDelete).not.toHaveBeenCalled();

        await userEvent.click(screen.getByRole('button', { name: 'yes' }));
        expect(onDelete).toHaveBeenCalledWith(1);
    });

    it('hides pagination for a single page', () => {
        render(<HoldingsTable page={page()} onEdit={noop} onDelete={noop} onPageChange={noop} />);

        expect(screen.queryByRole('button', { name: /prev/i })).not.toBeInTheDocument();
    });

    it('disables prev on the first page and pages forward otherwise', async () => {
        const onPageChange = vi.fn();
        render(<HoldingsTable
            page={page({ totalPages: 3, totalElements: 25 })}
            onEdit={noop}
            onDelete={noop}
            onPageChange={onPageChange}
        />);

        expect(screen.getByRole('button', { name: /prev/i })).toBeDisabled();

        await userEvent.click(screen.getByRole('button', { name: /next/i }));
        expect(onPageChange).toHaveBeenCalledWith(1);
    });
});
