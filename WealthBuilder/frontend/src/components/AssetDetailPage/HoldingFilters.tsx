import { useState } from 'react';
import { DatePicker } from '../DatePicker/DatePicker';
import { EMPTY_HOLDING_FILTER } from '../../types/holding';
import type { HoldingFilter } from '../../types/holding';
import styles from './HoldingFilters.module.css';


interface HoldingFiltersProps {
    filter: HoldingFilter;
    onChange: (filter: HoldingFilter) => void;
}


/**
 * Filter bar for the holdings table: a name search and an inclusive purchase-date range picked as
 * DD/MM/YYYY. Edits are kept in local draft state and only applied (pushed to the parent, which
 * refetches server-side) on submit or Enter. Clear resets both draft and applied.
 */
export const HoldingFilters = ({ filter, onChange }: HoldingFiltersProps) => {
    const [draft, setDraft] = useState<HoldingFilter>(filter);

    const update = (patch: Partial<HoldingFilter>): void => {
        setDraft((current) => ({ ...current, ...patch }));
    };

    const apply = (event: React.FormEvent<HTMLFormElement>): void => {
        event.preventDefault();
        onChange(draft);
    };

    const clear = (): void => {
        setDraft(EMPTY_HOLDING_FILTER);
        onChange(EMPTY_HOLDING_FILTER);
    };

    const hasActiveFilter = filter.name.length > 0 || filter.from.length > 0 || filter.to.length > 0;

    return (
        <form className={styles.bar} onSubmit={apply} noValidate>
            <label className={styles.field}>
                <span className={styles.label}>NAME</span>
                <input
                    className={styles.input}
                    type="text"
                    value={draft.name}
                    placeholder="search…"
                    onChange={(event) => update({ name: event.target.value })}
                />
            </label>

            <div className={styles.field}>
                <span className={styles.label}>FROM</span>
                <DatePicker
                    value={draft.from}
                    onChange={(iso) => update({ from: iso })}
                    ariaLabel="From date"
                    max={draft.to}
                />
            </div>

            <div className={styles.field}>
                <span className={styles.label}>TO</span>
                <DatePicker
                    value={draft.to}
                    onChange={(iso) => update({ to: iso })}
                    ariaLabel="To date"
                    min={draft.from}
                />
            </div>

            <button type="submit" className={styles.apply}>apply</button>

            {hasActiveFilter && (
                <button type="button" className={styles.clear} onClick={clear}>clear</button>
            )}
        </form>
    );
};
