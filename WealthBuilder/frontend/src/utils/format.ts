// Display formatters for the holdings UI. The app has no fixed currency, so money is rendered
// as a plain grouped decimal; quantity keeps up to 8 fractional digits (matching the column)
// with trailing zeros trimmed. The locale is pinned so output is deterministic regardless of
// the user's OS locale (a finance UI shouldn't flip its decimal separator per machine).
//
// Thousands are grouped with a space rather than a comma (the Bulgarian convention), while the
// decimal stays a dot. We format with en-US for a stable structure, then swap the comma grouping
// for a non-breaking space so the number never wraps onto two lines.

const LOCALE = 'en-US';

// A non-breaking space (U+00A0), so the grouped number can't wrap mid-value.
const GROUPING_SPACE = ' ';

const moneyFormatter = new Intl.NumberFormat(LOCALE, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
});

const quantityFormatter = new Intl.NumberFormat(LOCALE, {
    minimumFractionDigits: 0,
    maximumFractionDigits: 8,
});

const priceFormatter = new Intl.NumberFormat(LOCALE, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
});

const spaceGrouped = (formatted: string): string => formatted.replace(/,/g, GROUPING_SPACE);


export const formatMoney = (value: number): string => spaceGrouped(moneyFormatter.format(value));

export const formatQuantity = (value: number): string => spaceGrouped(quantityFormatter.format(value));

export const formatPrice = (value: number): string => spaceGrouped(priceFormatter.format(value));
