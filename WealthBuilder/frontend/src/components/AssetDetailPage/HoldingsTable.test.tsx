import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HoldingsTable } from './HoldingsTable';
import type { Holding, HoldingSummary } from '../../types/holding';
import type { PageResponse } from '../../types/page';


const holding = (overrides: Partial<Holding> = {}): Holding => ({
    id: 1,
    assetId: 7,
    name: 'Apple',
    boughtForAmount: 500,
    unit: 'shares',
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

const summary = (overrides: Partial<HoldingSummary> = {}): HoldingSummary => ({
    holdingCount: 2,
    averagePrice: 94.3333,
    quantitySum: 3,
    amountSum: 283,
    periodStart: '2026-06-01',
    periodEnd: '2026-06-19',
    ...overrides,
});

const noop = () => { };

const ADD_FIRST = 'No holdings yet. Add your first purchase.';


describe('HoldingsTable', () => {
    it('shows the empty label when there are no holdings', () => {
        render(<HoldingsTable page={page({ content: [], totalElements: 0 })} summary={null} loading={false} emptyLabel={ADD_FIRST} onEdit={noop} onDelete={noop} onPageChange={noop} onRowClick={noop} />);

        expect(screen.getByText(ADD_FIRST)).toBeInTheDocument();
    });

    it('shows a filtered empty label when one is given', () => {
        render(<HoldingsTable page={page({ content: [], totalElements: 0 })} summary={null} loading={false} emptyLabel="No holdings match your filters." onEdit={noop} onDelete={noop} onPageChange={noop} onRowClick={noop} />);

        expect(screen.getByText('No holdings match your filters.')).toBeInTheDocument();
    });

    it('renders a row with the formatted figures', () => {
        render(<HoldingsTable page={page()} summary={null} loading={false} emptyLabel={ADD_FIRST} onEdit={noop} onDelete={noop} onPageChange={noop} onRowClick={noop} />);

        expect(screen.getByText('Apple')).toBeInTheDocument();
        expect(screen.getByText('01/03/2026')).toBeInTheDocument();
        expect(screen.getByText('500.00')).toBeInTheDocument();
    });

    it('renders the filter totals row when a summary is given', () => {
        render(<HoldingsTable page={page()} summary={summary()} loading={false} emptyLabel={ADD_FIRST} onEdit={noop} onDelete={noop} onPageChange={noop} onRowClick={noop} />);

        expect(screen.getByText('totals (2)')).toBeInTheDocument();
        expect(screen.getByText('3')).toBeInTheDocument();
        expect(screen.getByText('94.3333')).toBeInTheDocument();
        expect(screen.getByText('283.00')).toBeInTheDocument();
    });

    it('omits the totals row when no summary is given', () => {
        render(<HoldingsTable page={page()} summary={null} loading={false} emptyLabel={ADD_FIRST} onEdit={noop} onDelete={noop} onPageChange={noop} onRowClick={noop} />);

        expect(screen.queryByText(/totals/i)).not.toBeInTheDocument();
    });

    it('requires a second click to confirm a delete', async () => {
        const onDelete = vi.fn();
        render(<HoldingsTable page={page()} summary={null} loading={false} emptyLabel={ADD_FIRST} onEdit={noop} onDelete={onDelete} onPageChange={noop} onRowClick={noop} />);

        await userEvent.click(screen.getByRole('button', { name: 'delete' }));
        expect(onDelete).not.toHaveBeenCalled();

        await userEvent.click(screen.getByRole('button', { name: 'yes' }));
        expect(onDelete).toHaveBeenCalledWith(1);
    });

    it('opens the detail view when a row is clicked', async () => {
        const onRowClick = vi.fn();
        render(<HoldingsTable page={page()} summary={null} loading={false} emptyLabel={ADD_FIRST} onEdit={noop} onDelete={noop} onPageChange={noop} onRowClick={onRowClick} />);

        await userEvent.click(screen.getByText('Apple'));
        expect(onRowClick).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }));
    });

    it('does not open the detail view when a row action is clicked', async () => {
        const onRowClick = vi.fn();
        render(<HoldingsTable page={page()} summary={null} loading={false} emptyLabel={ADD_FIRST} onEdit={noop} onDelete={noop} onPageChange={noop} onRowClick={onRowClick} />);

        await userEvent.click(screen.getByRole('button', { name: 'edit' }));
        expect(onRowClick).not.toHaveBeenCalled();
    });

    it('always shows the pager, disabling prev on the first page', () => {
        render(<HoldingsTable page={page()} summary={null} loading={false} emptyLabel={ADD_FIRST} onEdit={noop} onDelete={noop} onPageChange={noop} onRowClick={noop} />);

        expect(screen.getByRole('button', { name: /prev/i })).toBeInTheDocument();
        expect(screen.getByText('page 1 of 1')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /prev/i })).toBeDisabled();
    });

    it('disables prev on the first page and pages forward otherwise', async () => {
        const onPageChange = vi.fn();
        render(<HoldingsTable
            page={page({ totalPages: 3, totalElements: 25 })}
            summary={null}
            loading={false}
            emptyLabel={ADD_FIRST}
            onEdit={noop}
            onDelete={noop}
            onPageChange={onPageChange}
            onRowClick={noop}
        />);

        expect(screen.getByRole('button', { name: /prev/i })).toBeDisabled();

        await userEvent.click(screen.getByRole('button', { name: /next/i }));
        expect(onPageChange).toHaveBeenCalledWith(1);
    });
});
