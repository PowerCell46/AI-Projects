import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AggregationPanel } from './AggregationPanel';
import type { HoldingSummary } from '../../types/holding';


const summary = (overrides: Partial<HoldingSummary> = {}): HoldingSummary => ({
    holdingCount: 3,
    averagePrice: 120.5,
    quantitySum: 4.5,
    amountSum: 950,
    periodStart: '2026-01-01',
    periodEnd: '2026-06-01',
    ...overrides,
});


describe('AggregationPanel', () => {
    it('renders nothing until a summary is available', () => {
        const { container } = render(<AggregationPanel summary={null} />);

        expect(container).toBeEmptyDOMElement();
    });

    it('shows the aggregated figures', () => {
        render(<AggregationPanel summary={summary()} />);

        expect(screen.getByText('3')).toBeInTheDocument();
        expect(screen.getByText('950.00')).toBeInTheDocument();
        expect(screen.getByText('4.5')).toBeInTheDocument();
        expect(screen.getByText('120.50')).toBeInTheDocument();
        expect(screen.getByText('2026-01-01 → 2026-06-01')).toBeInTheDocument();
    });

    it('collapses an equal period to a single date', () => {
        render(<AggregationPanel summary={summary({ periodStart: '2026-01-01', periodEnd: '2026-01-01' })} />);

        expect(screen.getByText('2026-01-01')).toBeInTheDocument();
    });

    it('shows an em dash for price and period when there are no holdings', () => {
        render(<AggregationPanel summary={summary({
            holdingCount: 0,
            averagePrice: null,
            quantitySum: 0,
            amountSum: 0,
            periodStart: null,
            periodEnd: null,
        })} />);

        expect(screen.getAllByText('—')).toHaveLength(2);
    });
});
