const MS_PER_MINUTE = 60_000;

const MINUTES_PER_HOUR = 60;

const HOURS_PER_DAY = 24;

const DAYS_PER_WEEK = 7;

const MONTH_ABBREVIATIONS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];

function formatCalendarDate(postedAt: Date, now: Date): string {
    const dayAndMonth = `${postedAt.getDate()} ${MONTH_ABBREVIATIONS[postedAt.getMonth()]}`;

    return postedAt.getFullYear() === now.getFullYear() ? dayAndMonth : `${dayAndMonth} ${postedAt.getFullYear()}`;
}

export function formatPostTime(createdAt: string, now: Date): string {
    const postedAt = new Date(createdAt);
    // A post stamped slightly ahead of this clock reads as just now, not as a negative age.
    const minutes = Math.max(0, Math.floor((now.getTime() - postedAt.getTime()) / MS_PER_MINUTE));
    const hours = Math.floor(minutes / MINUTES_PER_HOUR);
    const days = Math.floor(hours / HOURS_PER_DAY);

    if (minutes < 1) {
        return 'NOW';
    }

    if (hours < 1) {
        return `${minutes}M`;
    }

    if (days < 1) {
        return `${hours}H`;
    }

    if (days < DAYS_PER_WEEK) {
        return `${days}D`;
    }

    return formatCalendarDate(postedAt, now);
}
