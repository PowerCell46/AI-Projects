import { useEffect, useRef, useState } from 'react';
import { isoToDisplay, todayIso, toIso } from '../../utils/date';
import styles from './DatePicker.module.css';


interface DatePickerProps {
    // Selected date as an ISO LocalDate (YYYY-MM-DD), or '' when nothing is chosen.
    value: string;
    onChange: (iso: string) => void;
    // Accessible name for the trigger (the visible field label is a separate element).
    ariaLabel: string;
    // Optional inclusive bounds; days outside the range are disabled.
    min?: string;
    max?: string;
    // Mirrors the form-field error state onto the trigger for assistive tech.
    invalid?: boolean;
    describedBy?: string;
}


interface MonthView {
    year: number;
    month: number;
}


const WEEKDAYS = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];

const MONTH_NAMES = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December',
];

// Arrow keys move the roving focus by these day deltas; a week is 7 days.
const KEY_DELTAS: Record<string, number> = {
    ArrowLeft: -1,
    ArrowRight: 1,
    ArrowUp: -7,
    ArrowDown: 7,
};


/**
 * A self-contained date picker: a read-only field showing the selected date as DD/MM/YYYY and a
 * calendar popup for choosing one. Emits an ISO date so callers keep working in YYYY-MM-DD while
 * the user always sees and picks in DD/MM/YYYY, independent of the browser locale. The grid is
 * keyboard-navigable (arrows/Home/End) with a roving tabstop, and focus returns to the trigger
 * when the popup closes.
 */
export const DatePicker = ({ value, onChange, ariaLabel, min, max, invalid, describedBy }: DatePickerProps) => {
    const [open, setOpen] = useState(false);
    const [view, setView] = useState<MonthView>(() => initialView(value));
    const [focusedDay, setFocusedDay] = useState(1);
    const containerRef = useRef<HTMLDivElement>(null);
    const triggerRef = useRef<HTMLButtonElement>(null);
    const gridRef = useRef<HTMLDivElement>(null);

    // While open, close on an outside pointer (covers mouse, touch, and pen) or Escape.
    useEffect(() => {
        if (!open) {
            return;
        }

        const onPointerDown = (event: PointerEvent): void => {
            if (containerRef.current !== null && !containerRef.current.contains(event.target as Node)) {
                setOpen(false);
            }
        };

        const onKeyDown = (event: KeyboardEvent): void => {
            if (event.key === 'Escape') {
                setOpen(false);
                triggerRef.current?.focus();
            }
        };

        document.addEventListener('pointerdown', onPointerDown);
        document.addEventListener('keydown', onKeyDown);

        return () => {
            document.removeEventListener('pointerdown', onPointerDown);
            document.removeEventListener('keydown', onKeyDown);
        };
    }, [open]);

    // On open, move focus to the day carrying the roving tabstop so the grid is usable from the
    // keyboard immediately.
    useEffect(() => {
        if (open) {
            gridRef.current?.querySelector<HTMLButtonElement>('button[tabindex="0"]')?.focus();
        }
    }, [open]);

    const openCalendar = (): void => {
        const startView = initialView(value);
        setView(startView);
        setFocusedDay(initialFocusedDay(value, startView));
        setOpen(true);
    };

    const closeAndRestoreFocus = (): void => {
        setOpen(false);
        triggerRef.current?.focus();
    };

    const selectDay = (day: number): void => {
        onChange(toIso(view.year, view.month, day));
        closeAndRestoreFocus();
    };

    const clear = (): void => {
        onChange('');
        closeAndRestoreFocus();
    };

    const goToPreviousMonth = (): void => setView(shiftMonth(view, -1));

    const goToNextMonth = (): void => setView(shiftMonth(view, 1));

    // Move the roving focus within the month with the arrow keys plus Home/End.
    const handleGridKeyDown = (event: React.KeyboardEvent<HTMLDivElement>): void => {
        const daysInMonth = daysInMonthOf(view);
        const delta = KEY_DELTAS[event.key];

        const target = event.key === 'Home'
            ? 1
            : event.key === 'End'
                ? daysInMonth
                : delta !== undefined ? focusedDay + delta : null;

        if (target === null) {
            return;
        }

        event.preventDefault();

        const clamped = Math.min(Math.max(target, 1), daysInMonth);
        setFocusedDay(clamped);
        focusDay(clamped);
    };

    const focusDay = (day: number): void => {
        const button = gridRef.current?.querySelector<HTMLButtonElement>(`button[data-day="${day}"]`);
        button?.focus();
    };

    return (
        <div className={styles.container} ref={containerRef}>
            <button
                ref={triggerRef}
                type="button"
                className={styles.field}
                aria-label={ariaLabel}
                aria-invalid={invalid}
                aria-describedby={describedBy}
                aria-haspopup="dialog"
                aria-expanded={open}
                onClick={openCalendar}
            >
                <span className={value.length > 0 ? styles.value : styles.placeholder}>
                    {value.length > 0 ? isoToDisplay(value) : 'dd/mm/yyyy'}
                </span>
                <span className={styles.icon} aria-hidden="true">▤</span>
            </button>

            {open && (
                <div className={styles.popup} role="dialog" aria-label={ariaLabel}>
                    <div className={styles.header}>
                        <button
                            type="button"
                            className={styles.nav}
                            aria-label="Previous month"
                            onClick={goToPreviousMonth}
                        >
                            ‹
                        </button>

                        <span className={styles.monthLabel}>
                            {MONTH_NAMES[view.month]} {view.year}
                        </span>

                        <button
                            type="button"
                            className={styles.nav}
                            aria-label="Next month"
                            onClick={goToNextMonth}
                        >
                            ›
                        </button>
                    </div>

                    <div className={styles.weekdays} aria-hidden="true">
                        {WEEKDAYS.map((weekday) => (
                            <span key={weekday} className={styles.weekday}>{weekday}</span>
                        ))}
                    </div>

                    <div
                        className={styles.grid}
                        ref={gridRef}
                        role="grid"
                        aria-label={`${MONTH_NAMES[view.month]} ${view.year}`}
                        onKeyDown={handleGridKeyDown}
                    >
                        {buildCells(view).map((day, index) => (
                            day === null
                                ? <span key={`blank-${index}`} className={styles.blank} />
                                : (
                                    <button
                                        key={day}
                                        type="button"
                                        data-day={day}
                                        className={dayClassName(view, day, value)}
                                        disabled={isOutOfRange(view, day, min, max)}
                                        tabIndex={day === focusedDay ? 0 : -1}
                                        aria-current={toIso(view.year, view.month, day) === value ? 'date' : undefined}
                                        onClick={() => selectDay(day)}
                                    >
                                        {day}
                                    </button>
                                )
                        ))}
                    </div>

                    <div className={styles.footer}>
                        <button type="button" className={styles.clear} onClick={clear}>clear</button>
                    </div>
                </div>
            )}
        </div>
    );
};


const initialView = (iso: string): MonthView => {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);

    if (match !== null) {
        return { year: Number(match[1]), month: Number(match[2]) - 1 };
    }

    const now = new Date();

    return { year: now.getFullYear(), month: now.getMonth() };
};

// The day to land the roving focus on when the popup opens: the selected day if it's in view,
// otherwise the first of the month.
const initialFocusedDay = (iso: string, view: MonthView): number => {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);

    if (match !== null && Number(match[1]) === view.year && Number(match[2]) - 1 === view.month) {
        return Number(match[3]);
    }

    return 1;
};

const shiftMonth = (view: MonthView, delta: number): MonthView => {
    const date = new Date(view.year, view.month + delta, 1);

    return { year: date.getFullYear(), month: date.getMonth() };
};

const daysInMonthOf = (view: MonthView): number => new Date(view.year, view.month + 1, 0).getDate();

/**
 * The cells for a month grid: leading blanks so the first day lands under its weekday (week
 * starts Monday), then one entry per day of the month.
 */
const buildCells = (view: MonthView): Array<number | null> => {
    const firstWeekday = (new Date(view.year, view.month, 1).getDay() + 6) % 7;
    const daysInMonth = daysInMonthOf(view);

    const leadingBlanks: Array<number | null> = Array(firstWeekday).fill(null);
    const days = Array.from({ length: daysInMonth }, (_, index) => index + 1);

    return [...leadingBlanks, ...days];
};

const isOutOfRange = (view: MonthView, day: number, min?: string, max?: string): boolean => {
    const iso = toIso(view.year, view.month, day);

    return (min !== undefined && min.length > 0 && iso < min)
        || (max !== undefined && max.length > 0 && iso > max);
};

const dayClassName = (view: MonthView, day: number, value: string): string => {
    const iso = toIso(view.year, view.month, day);
    const classes = [styles.day];

    if (iso === value) {
        classes.push(styles.selected);
    } else if (iso === todayIso()) {
        classes.push(styles.today);
    }

    return classes.join(' ');
};
