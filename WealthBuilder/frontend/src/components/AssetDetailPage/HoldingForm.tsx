import { useState } from 'react';
import { createHolding, updateHolding } from '../../services/holdingService';
import { todayIso } from '../../utils/date';
import { DatePicker } from '../DatePicker/DatePicker';
import { ApiError } from '../../types/problem';
import type { Holding, HoldingRequest } from '../../types/holding';
import styles from './HoldingForm.module.css';


interface HoldingFormProps {
    assetId: number;
    // The holding being edited, or null when adding a new one.
    holding: Holding | null;
    onSaved: () => void;
    onClose: () => void;
}


type FieldErrors = Partial<Record<keyof HoldingRequest, string>>;


/**
 * Modal create/edit form for a single holding. Client validation mirrors the server contract
 * (positive amount/quantity, non-future date) so the common cases fail fast without a round trip.
 */
export const HoldingForm = ({ assetId, holding, onSaved, onClose }: HoldingFormProps) => {
    const isEdit = holding !== null;

    const [name, setName] = useState(holding?.name ?? '');
    const [amount, setAmount] = useState(holding ? String(holding.boughtForAmount) : '');
    const [quantity, setQuantity] = useState(holding ? String(holding.quantity) : '');
    // ISO date (YYYY-MM-DD); the DatePicker shows and selects it as DD/MM/YYYY.
    const [date, setDate] = useState(holding?.date ?? '');
    const [note, setNote] = useState(holding?.note ?? '');

    const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
    const [formError, setFormError] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    const handleSubmit = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
        event.preventDefault();

        const clientErrors = validate();
        if (Object.keys(clientErrors).length > 0) {
            setFieldErrors(clientErrors);
            return;
        }

        setSubmitting(true);
        setFormError(null);
        setFieldErrors({});

        try {
            await persist(buildRequest());
            onSaved();
        } catch (error) {
            applyError(error);
        } finally {
            setSubmitting(false);
        }
    };

    const validate = (): FieldErrors => {
        const errors: FieldErrors = {};

        if (name.trim().length === 0) {
            errors.name = 'Name is required.';
        }
        if (!isPositiveNumber(amount)) {
            errors.boughtForAmount = 'Enter an amount greater than 0.';
        }
        if (!isPositiveNumber(quantity)) {
            errors.quantity = 'Enter a quantity greater than 0.';
        }

        if (date.length === 0) {
            errors.date = 'Purchase date is required.';
        }

        return errors;
    };

    const buildRequest = (): HoldingRequest => ({
        name: name.trim(),
        boughtForAmount: Number(amount),
        quantity: Number(quantity),
        date,
        note: note.trim().length > 0 ? note.trim() : null,
    });

    const persist = (request: HoldingRequest): Promise<Holding> => {
        if (isEdit) {
            return updateHolding(holding.id, request);
        }

        return createHolding(assetId, request);
    };

    const applyError = (error: unknown): void => {
        if (error instanceof ApiError) {
            setFieldErrors(error.fieldErrors as FieldErrors);
            setFormError(Object.keys(error.fieldErrors).length > 0 ? null : error.detail);
            return;
        }

        setFormError('Could not reach the server. Try again.');
    };

    return (
        <div className={styles.backdrop} onClick={onClose} role="presentation">
            <form
                className={styles.form}
                onClick={(event) => event.stopPropagation()}
                onSubmit={handleSubmit}
                noValidate
            >
                <h2 className={styles.heading}>{isEdit ? 'EDIT HOLDING' : 'NEW HOLDING'}</h2>

                <label className={styles.field}>
                    <span className={styles.label}>NAME</span>
                    <input
                        className={styles.input}
                        type="text"
                        value={name}
                        maxLength={200}
                        autoFocus
                        onChange={(event) => setName(event.target.value)}
                    />
                    <FieldError message={fieldErrors.name} />
                </label>

                <div className={styles.pair}>
                    <label className={styles.field}>
                        <span className={styles.label}>AMOUNT</span>
                        <input
                            className={styles.input}
                            type="number"
                            min="0"
                            step="any"
                            value={amount}
                            onChange={(event) => setAmount(event.target.value)}
                        />
                        <FieldError message={fieldErrors.boughtForAmount} />
                    </label>

                    <label className={styles.field}>
                        <span className={styles.label}>QUANTITY</span>
                        <input
                            className={styles.input}
                            type="number"
                            min="0"
                            step="any"
                            value={quantity}
                            onChange={(event) => setQuantity(event.target.value)}
                        />
                        <FieldError message={fieldErrors.quantity} />
                    </label>
                </div>

                <div className={styles.field}>
                    <span className={styles.label}>PURCHASE DATE</span>
                    <DatePicker
                        value={date}
                        onChange={setDate}
                        ariaLabel="Purchase date"
                        max={todayIso()}
                    />
                    <FieldError message={fieldErrors.date} />
                </div>

                <label className={styles.field}>
                    <span className={styles.label}>NOTE (OPTIONAL)</span>
                    <textarea
                        className={styles.textarea}
                        value={note}
                        maxLength={1000}
                        rows={3}
                        onChange={(event) => setNote(event.target.value)}
                    />
                    <FieldError message={fieldErrors.note} />
                </label>

                {formError !== null && (
                    <p className={styles.formError} role="alert">! {formError}</p>
                )}

                <div className={styles.actions}>
                    <button type="submit" className={styles.save} disabled={submitting}>
                        {submitting ? '[ ... ]' : 'save'}
                    </button>

                    <button type="button" className={styles.cancel} onClick={onClose}>
                        cancel
                    </button>
                </div>
            </form>
        </div>
    );
};


const FieldError = ({ message }: { message: string | undefined }) => {
    if (message === undefined) {
        return null;
    }

    return <span className={styles.fieldError}>{message}</span>;
};


const isPositiveNumber = (value: string): boolean => {
    const parsed = Number(value);

    return value.trim().length > 0 && Number.isFinite(parsed) && parsed > 0;
};
