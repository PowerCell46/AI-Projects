import { useState } from 'react';
import { createHolding, updateHolding } from '../../services/holdingService';
import { todayIso } from '../../utils/date';
import { DatePicker } from '../DatePicker/DatePicker';
import { useModalBehavior } from '../../hooks/useModalBehavior';
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


// Keeps only the keys this form actually renders, so an unexpected server field doesn't get
// silently dropped into state where nothing displays it.
const pickFieldErrors = (errors: Record<string, string>): FieldErrors => {
    const mapped: FieldErrors = {};

    if (errors.name !== undefined) {
        mapped.name = errors.name;
    }
    if (errors.boughtForAmount !== undefined) {
        mapped.boughtForAmount = errors.boughtForAmount;
    }
    if (errors.unit !== undefined) {
        mapped.unit = errors.unit;
    }
    if (errors.quantity !== undefined) {
        mapped.quantity = errors.quantity;
    }
    if (errors.date !== undefined) {
        mapped.date = errors.date;
    }
    if (errors.note !== undefined) {
        mapped.note = errors.note;
    }

    return mapped;
};


/**
 * Modal create/edit form for a single holding. Client validation mirrors the server contract
 * (positive amount/quantity, non-future date) so the common cases fail fast without a round trip.
 */
export const HoldingForm = ({ assetId, holding, onSaved, onClose }: HoldingFormProps) => {
    const isEdit = holding !== null;

    const formRef = useModalBehavior<HTMLFormElement>(onClose);

    const [name, setName] = useState(holding?.name ?? '');
    const [amount, setAmount] = useState(holding ? String(holding.boughtForAmount) : '');
    const [unit, setUnit] = useState(holding?.unit ?? '');
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
            errors.boughtForAmount = 'Enter a total cost greater than 0.';
        }
        if (unit.trim().length === 0) {
            errors.unit = 'Unit is required.';
        }
        if (!isPositiveNumber(quantity)) {
            errors.quantity = 'Enter a quantity greater than 0.';
        }

        if (date.length === 0) {
            errors.date = 'Purchase date is required.';
        } else if (date > todayIso()) {
            errors.date = 'Purchase date can’t be in the future.';
        }

        return errors;
    };

    const buildRequest = (): HoldingRequest => ({
        name: name.trim(),
        boughtForAmount: Number(amount),
        unit: unit.trim(),
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
            const mapped = pickFieldErrors(error.fieldErrors);

            setFieldErrors(mapped);
            setFormError(Object.keys(mapped).length > 0 ? null : error.detail);
            return;
        }

        setFormError('Could not reach the server. Try again.');
    };

    return (
        <div className={styles.backdrop} onClick={onClose} role="presentation">
            <form
                ref={formRef}
                className={styles.form}
                onClick={(event) => event.stopPropagation()}
                onSubmit={handleSubmit}
                role="dialog"
                aria-modal="true"
                aria-label={isEdit ? 'Edit holding' : 'New holding'}
                tabIndex={-1}
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
                        aria-invalid={fieldErrors.name !== undefined}
                        aria-describedby={fieldErrors.name !== undefined ? 'holding-name-error' : undefined}
                        onChange={(event) => setName(event.target.value)}
                    />
                    <FieldError id="holding-name-error" message={fieldErrors.name} />
                </label>

                <label className={styles.field}>
                    <span className={styles.label}>TOTAL COST</span>
                    <input
                        className={styles.input}
                        type="number"
                        min="0"
                        step="any"
                        value={amount}
                        aria-invalid={fieldErrors.boughtForAmount !== undefined}
                        aria-describedby={fieldErrors.boughtForAmount !== undefined ? 'holding-amount-error' : undefined}
                        onChange={(event) => setAmount(event.target.value)}
                    />
                    <FieldError id="holding-amount-error" message={fieldErrors.boughtForAmount} />
                </label>

                <div className={styles.pair}>
                    <label className={styles.field}>
                        <span className={styles.label}>UNIT</span>
                        <input
                            className={styles.input}
                            type="text"
                            value={unit}
                            maxLength={30}
                            aria-invalid={fieldErrors.unit !== undefined}
                            aria-describedby={fieldErrors.unit !== undefined ? 'holding-unit-error' : undefined}
                            onChange={(event) => setUnit(event.target.value)}
                        />
                        <FieldError id="holding-unit-error" message={fieldErrors.unit} />
                    </label>

                    <label className={styles.field}>
                        <span className={styles.label}>QUANTITY</span>
                        <input
                            className={styles.input}
                            type="number"
                            min="0"
                            step="any"
                            value={quantity}
                            aria-invalid={fieldErrors.quantity !== undefined}
                            aria-describedby={fieldErrors.quantity !== undefined ? 'holding-quantity-error' : undefined}
                            onChange={(event) => setQuantity(event.target.value)}
                        />
                        <FieldError id="holding-quantity-error" message={fieldErrors.quantity} />
                    </label>
                </div>

                <div className={styles.field}>
                    <span className={styles.label}>PURCHASE DATE</span>
                    <DatePicker
                        value={date}
                        onChange={setDate}
                        ariaLabel="Purchase date"
                        max={todayIso()}
                        invalid={fieldErrors.date !== undefined}
                        describedBy={fieldErrors.date !== undefined ? 'holding-date-error' : undefined}
                    />
                    <FieldError id="holding-date-error" message={fieldErrors.date} />
                </div>

                <label className={styles.field}>
                    <span className={styles.label}>NOTE (OPTIONAL)</span>
                    <textarea
                        className={styles.textarea}
                        value={note}
                        maxLength={1000}
                        rows={3}
                        aria-invalid={fieldErrors.note !== undefined}
                        aria-describedby={fieldErrors.note !== undefined ? 'holding-note-error' : undefined}
                        onChange={(event) => setNote(event.target.value)}
                    />
                    <FieldError id="holding-note-error" message={fieldErrors.note} />
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


const FieldError = ({ id, message }: { id: string; message: string | undefined }) => {
    if (message === undefined) {
        return null;
    }

    return <span id={id} className={styles.fieldError}>{message}</span>;
};


const isPositiveNumber = (value: string): boolean => {
    const parsed = Number(value);

    return value.trim().length > 0 && Number.isFinite(parsed) && parsed > 0;
};
