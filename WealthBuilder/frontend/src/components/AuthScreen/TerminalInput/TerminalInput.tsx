import { useState } from 'react';
import styles from './TerminalInput.module.css';


interface TerminalInputProps {
    id: string;
    label: string;
    type: 'text' | 'password';
    value: string;
    autoComplete: string;
    disabled: boolean;
    error?: string;
    // When true, renders a show/hide toggle for masked (password) fields.
    revealable?: boolean;
    onChange: (value: string) => void;
}


/**
 * A single terminal field: a phosphor eyebrow label, an inline `:` prompt glyph, the
 * input itself, and an optional red error helper line below. A revealable field gains an
 * inline show/hide toggle that flips the input between masked and plain text.
 */
export const TerminalInput = ({
    id,
    label,
    type,
    value,
    autoComplete,
    disabled,
    error,
    revealable = false,
    onChange,
}: TerminalInputProps) => {
    const [revealed, setRevealed] = useState(false);

    const hasError = error !== undefined;
    const helperId = `${id}-helper`;

    const effectiveType = revealable && revealed ? 'text' : type;

    return (
        <div className={styles.field}>
            <label htmlFor={id} className={styles.label}>{label}</label>

            <div className={`${styles.inputRow} ${hasError ? styles.errored : ''}`}>
                <span className={styles.prompt} aria-hidden="true">:</span>

                <input
                    id={id}
                    className={styles.input}
                    type={effectiveType}
                    value={value}
                    autoComplete={autoComplete}
                    disabled={disabled}
                    aria-invalid={hasError}
                    aria-describedby={hasError ? helperId : undefined}
                    onChange={(event) => onChange(event.target.value)}
                />

                {revealable && (
                    <button
                        type="button"
                        className={styles.reveal}
                        onClick={() => setRevealed((current) => !current)}
                        disabled={disabled}
                        aria-pressed={revealed}
                        aria-label={revealed ? 'Hide password' : 'Show password'}
                    >
                        {revealed ? 'hide' : 'show'}
                    </button>
                )}
            </div>

            {hasError && (
                <p id={helperId} className={styles.helper}>! {error}</p>
            )}
        </div>
    );
};
