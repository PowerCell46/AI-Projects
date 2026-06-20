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


/**
 * A self-contained date picker: a read-only field showing the selected date as DD/MM/YYYY and a
 * calendar popup for choosing one. Emits an ISO date so callers keep working in YYYY-MM-DD while
 * the user always sees and picks in DD/MM/YYYY, independent of the browser locale.
 */
export const DatePicker = ({ value, onChange, ariaLabel, min, max }: DatePickerProps) => {
    const [open, setOpen] = useState(false);
    const [view, setView] = useState<MonthView>(() => initialView(value));
    const containerRef = useRef<HTMLDivElement>(null);

    // While open, close on an outside click or Escape.
    useEffect(() => {
        if (!open) {
            return;
        }

        const onPointerDown = (event: MouseEvent): void => {
            if (containerRef.current !== null && !containerRef.current.contains(event.target as Node)) {
                setOpen(false);
            }
        };

        const onKeyDown = (event: KeyboardEvent): void => {
            if (event.key === 'Escape') {
                setOpen(false);
            }
        };

        document.addEventListener('mousedown', onPointerDown);
        document.addEventListener('keydown', onKeyDown);

        return () => {
            document.removeEventListener('mousedown', onPointerDown);
            document.removeEventListener('keydown', onKeyDown);
        };
    }, [open]);

    const openCalendar = (): void => {
        setView(initialView(value));
        setOpen(true);
    };

    const selectDay = (day: number): void => {
        onChange(toIso(view.year, view.month, day));
        setOpen(false);
    };

    const clear = (): void => {
        onChange('');
        setOpen(false);
    };

    const goToPreviousMonth = (): void => setView(shiftMonth(view, -1));

    const goToNextMonth = (): void => setView(shiftMonth(view, 1));

    return (
        <div className={styles.container} ref={containerRef}>
            <button
                type="button"
                className={styles.field}
                aria-label={ariaLabel}
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

                    <div className={styles.weekdays}>
                        {WEEKDAYS.map((weekday) => (
                            <span key={weekday} className={styles.weekday}>{weekday}</span>
                        ))}
                    </div>

                    <div className={styles.grid}>
                        {buildCells(view).map((day, index) => (
                            day === null
                                ? <span key={`blank-${index}`} className={styles.blank} />
                                : (
                                    <button
                                        key={day}
                                        type="button"
                                        className={dayClassName(view, day, value)}
                                        disabled={isOutOfRange(view, day, min, max)}
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

const shiftMonth = (view: MonthView, delta: number): MonthView => {
    const date = new Date(view.year, view.month + delta, 1);

    return { year: date.getFullYear(), month: date.getMonth() };
};

/**
 * The cells for a month grid: leading blanks so the first day lands under its weekday (week
 * starts Monday), then one entry per day of the month.
 */
const buildCells = (view: MonthView): Array<number | null> => {
    const firstWeekday = (new Date(view.year, view.month, 1).getDay() + 6) % 7;
    const daysInMonth = new Date(view.year, view.month + 1, 0).getDate();

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
