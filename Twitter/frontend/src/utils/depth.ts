export const TICK_INTERVALS = 26;

export const MAJOR_TICK_EVERY = 4;

const TOP_OFFSET_PERCENT = 6;

const SPAN_PERCENT = 86;

const POSITION_EXPONENT = 0.78;

const DEPTH_EXPONENT = 1.55;

const DEEPEST_LABEL_METRES = 11000;

const LABEL_ROUNDING_METRES = 50;

function positionPercent(fraction: number): number {
    return TOP_OFFSET_PERCENT + SPAN_PERCENT * fraction ** POSITION_EXPONENT;
}

export function isMajorTick(tickIndex: number): boolean {
    return tickIndex % MAJOR_TICK_EVERY === 0;
}

export function tickPositionPercent(tickIndex: number): number {
    return positionPercent(tickIndex / TICK_INTERVALS);
}

export function tickLabelMetres(tickIndex: number): number {
    const fraction = tickIndex / TICK_INTERVALS;
    const rawMetres = DEEPEST_LABEL_METRES * fraction ** DEPTH_EXPONENT;

    return Math.round(rawMetres / LABEL_ROUNDING_METRES) * LABEL_ROUNDING_METRES;
}

export function depthPositionPercent(depthMetres: number, seafloorDepthMetres: number): number {
    const fraction = (depthMetres / seafloorDepthMetres) ** (1 / DEPTH_EXPONENT);

    return positionPercent(fraction);
}
