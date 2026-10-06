const THOUSAND = 1_000;

const MILLION = 1_000_000;

const TENTHS_PER_UNIT = 10;

// Below ten units one decimal is shown, from ten units on whole units.
const TEN_UNITS_IN_TENTHS = 100;

function formatScaled(count: number, unit: number, suffix: string): string {
    const tenths = Math.floor(count / (unit / TENTHS_PER_UNIT));

    if (tenths >= TEN_UNITS_IN_TENTHS) {
        return `${Math.floor(count / unit)}${suffix}`;
    }

    const wholeUnits = Math.floor(tenths / TENTHS_PER_UNIT);
    const tenthsOfUnit = tenths % TENTHS_PER_UNIT;

    return tenthsOfUnit === 0 ? `${wholeUnits}${suffix}` : `${wholeUnits}.${tenthsOfUnit}${suffix}`;
}

// Always rounded down, so a count never reads higher than it is: 1,299 is 1.2K, never 1.3K.
export function formatCount(count: number): string {
    if (count < THOUSAND) {
        return String(count);
    }

    if (count < MILLION) {
        return formatScaled(count, THOUSAND, 'K');
    }

    return formatScaled(count, MILLION, 'M');
}
