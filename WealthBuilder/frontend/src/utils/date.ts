// Dates cross the wire as ISO LocalDate (YYYY-MM-DD) but are shown as DD/MM/YYYY. Native
// <input type="date"> can't be forced to that format (it follows the browser locale), so the UI
// uses a custom DatePicker plus these helpers and keeps ISO on the wire.

const ISO_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;


export const isoToDisplay = (iso: string): string => {
    const match = ISO_PATTERN.exec(iso);

    if (match === null) {
        return iso;
    }

    const [, year, month, day] = match;

    return `${day}/${month}/${year}`;
};

export const todayIso = (): string => {
    const now = new Date();

    return toIso(now.getFullYear(), now.getMonth(), now.getDate());
};

// Builds an ISO date from calendar parts (month is 0-based, matching the Date API).
export const toIso = (year: number, monthIndex: number, day: number): string => {
    const month = String(monthIndex + 1).padStart(2, '0');
    const paddedDay = String(day).padStart(2, '0');

    return `${year}-${month}-${paddedDay}`;
};
