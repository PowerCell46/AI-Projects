// Display formatters for the holdings UI. The app has no fixed currency, so money is rendered
// as a plain grouped decimal; quantity keeps up to 8 fractional digits (matching the column)
// with trailing zeros trimmed. The locale is pinned so output is deterministic regardless of
// the user's OS locale (a finance UI shouldn't flip its decimal separator per machine).

const LOCALE = 'en-US';

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


export const formatMoney = (value: number): string => moneyFormatter.format(value);

export const formatQuantity = (value: number): string => quantityFormatter.format(value);

export const formatPrice = (value: number): string => priceFormatter.format(value);
