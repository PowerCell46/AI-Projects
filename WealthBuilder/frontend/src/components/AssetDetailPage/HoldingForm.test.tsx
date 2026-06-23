import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HoldingForm } from './HoldingForm';
import { createHolding } from '../../services/holdingService';


vi.mock('../../services/holdingService', () => ({
    createHolding: vi.fn(),
    updateHolding: vi.fn(),
}));

const mockedCreate = vi.mocked(createHolding);


// Picks the 1st of the currently shown month in the open calendar, and returns its ISO date so
// the expectation stays in step with the test clock (the 1st is always today-or-earlier).
const pickFirstOfThisMonth = async (): Promise<string> => {
    await userEvent.click(screen.getByRole('button', { name: 'Purchase date' }));
    await userEvent.click(screen.getByRole('button', { name: '1' }));

    const now = new Date();
    const month = String(now.getMonth() + 1).padStart(2, '0');

    return `${now.getFullYear()}-${month}-01`;
};


describe('HoldingForm', () => {
    beforeEach(() => {
        mockedCreate.mockReset();
    });

    it('blocks submit and shows field errors when required fields are empty', async () => {
        render(<HoldingForm assetId={7} holding={null} onSaved={vi.fn()} onClose={vi.fn()} />);

        await userEvent.click(screen.getByRole('button', { name: 'save' }));

        expect(screen.getByText('Name is required.')).toBeInTheDocument();
        expect(screen.getByText('Enter a total cost greater than 0.')).toBeInTheDocument();
        expect(screen.getByText('Unit is required.')).toBeInTheDocument();
        expect(screen.getByText('Enter a quantity greater than 0.')).toBeInTheDocument();
        expect(screen.getByText('Purchase date is required.')).toBeInTheDocument();
        expect(mockedCreate).not.toHaveBeenCalled();
    });

    it('rejects a zero or negative total cost', async () => {
        render(<HoldingForm assetId={7} holding={null} onSaved={vi.fn()} onClose={vi.fn()} />);

        await userEvent.type(screen.getByLabelText('NAME'), 'Apple');
        await userEvent.type(screen.getByLabelText('TOTAL COST'), '0');
        await userEvent.type(screen.getByLabelText('QUANTITY'), '2');
        await userEvent.click(screen.getByRole('button', { name: 'save' }));

        expect(screen.getByText('Enter a total cost greater than 0.')).toBeInTheDocument();
        expect(mockedCreate).not.toHaveBeenCalled();
    });

    it('creates the holding from a calendar-picked date when the form is valid', async () => {
        const onSaved = vi.fn();
        mockedCreate.mockResolvedValue({
            id: 1,
            version: 0,
            assetId: 7,
            name: 'Apple',
            boughtForAmount: 500,
            unit: 'shares',
            quantity: 2,
            price: 250,
            date: '2026-03-01',
            note: null,
            createdAt: '2026-03-01T10:00:00Z',
        });

        render(<HoldingForm assetId={7} holding={null} onSaved={onSaved} onClose={vi.fn()} />);

        await userEvent.type(screen.getByLabelText('NAME'), 'Apple');
        await userEvent.type(screen.getByLabelText('TOTAL COST'), '500');
        await userEvent.type(screen.getByLabelText('UNIT'), 'shares');
        await userEvent.type(screen.getByLabelText('QUANTITY'), '2');
        const expectedDate = await pickFirstOfThisMonth();
        await userEvent.click(screen.getByRole('button', { name: 'save' }));

        expect(mockedCreate).toHaveBeenCalledWith(7, {
            name: 'Apple',
            boughtForAmount: 500,
            unit: 'shares',
            quantity: 2,
            date: expectedDate,
            note: null,
        });
        expect(onSaved).toHaveBeenCalledOnce();
    });
});
