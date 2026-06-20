import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HoldingForm } from './HoldingForm';
import { createHolding } from '../../services/holdingService';


vi.mock('../../services/holdingService', () => ({
    createHolding: vi.fn(),
    updateHolding: vi.fn(),
}));

const mockedCreate = vi.mocked(createHolding);


describe('HoldingForm', () => {
    beforeEach(() => {
        mockedCreate.mockReset();
    });

    it('blocks submit and shows field errors when required fields are empty', async () => {
        render(<HoldingForm assetId={7} holding={null} onSaved={vi.fn()} onClose={vi.fn()} />);

        await userEvent.click(screen.getByRole('button', { name: 'save' }));

        expect(screen.getByText('Name is required.')).toBeInTheDocument();
        expect(screen.getByText('Enter an amount greater than 0.')).toBeInTheDocument();
        expect(screen.getByText('Enter a quantity greater than 0.')).toBeInTheDocument();
        expect(mockedCreate).not.toHaveBeenCalled();
    });

    it('rejects a zero or negative amount', async () => {
        render(<HoldingForm assetId={7} holding={null} onSaved={vi.fn()} onClose={vi.fn()} />);

        await userEvent.type(screen.getByLabelText('NAME'), 'Apple');
        await userEvent.type(screen.getByLabelText('AMOUNT'), '0');
        await userEvent.type(screen.getByLabelText('QUANTITY'), '2');
        fireEvent.change(screen.getByLabelText('PURCHASE DATE'), { target: { value: '2026-03-01' } });
        await userEvent.click(screen.getByRole('button', { name: 'save' }));

        expect(screen.getByText('Enter an amount greater than 0.')).toBeInTheDocument();
        expect(mockedCreate).not.toHaveBeenCalled();
    });

    it('creates the holding and signals success when the form is valid', async () => {
        const onSaved = vi.fn();
        mockedCreate.mockResolvedValue({
            id: 1,
            assetId: 7,
            name: 'Apple',
            boughtForAmount: 500,
            quantity: 2,
            price: 250,
            date: '2026-03-01',
            note: null,
            createdAt: '2026-03-01T10:00:00Z',
        });

        render(<HoldingForm assetId={7} holding={null} onSaved={onSaved} onClose={vi.fn()} />);

        await userEvent.type(screen.getByLabelText('NAME'), 'Apple');
        await userEvent.type(screen.getByLabelText('AMOUNT'), '500');
        await userEvent.type(screen.getByLabelText('QUANTITY'), '2');
        fireEvent.change(screen.getByLabelText('PURCHASE DATE'), { target: { value: '2026-03-01' } });
        await userEvent.click(screen.getByRole('button', { name: 'save' }));

        expect(mockedCreate).toHaveBeenCalledWith(7, {
            name: 'Apple',
            boughtForAmount: 500,
            quantity: 2,
            date: '2026-03-01',
            note: null,
        });
        expect(onSaved).toHaveBeenCalledOnce();
    });
});
