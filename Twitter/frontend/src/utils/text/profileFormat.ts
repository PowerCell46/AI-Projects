const LOCALE = 'en-US';

// The month and year the account was created, read in UTC so the same account never shows two different months.
export function formatJoinDate(createdAt: string): string {
    const created = new Date(createdAt);
    const month = created
        .toLocaleString(LOCALE, {
            month: 'short',
            timeZone: 'UTC',
        })
        .toUpperCase();

    return `JOINED ${month} ${created.getUTCFullYear()}`;
}

// A profile shows the whole number (1,204), unlike a card or a post, which round it down (1.2K).
export function formatFullCount(count: number): string {
    return count.toLocaleString(LOCALE);
}

export function pluralLabel(count: number, singular: string, plural: string): string {
    return count === 1 ? singular : plural;
}
