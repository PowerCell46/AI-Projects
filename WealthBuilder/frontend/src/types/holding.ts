// Domain types mirroring the backend holding contract (see HoldingController + DTOs).
// Money/quantity arrive as JSON numbers; `date` is an ISO `LocalDate` (YYYY-MM-DD) and
// `createdAt` an ISO `Instant`.

export interface Holding {
    id: number;
    assetId: number;
    name: string;
    boughtForAmount: number;
    quantity: number;
    price: number;
    date: string;
    note: string | null;
    createdAt: string;
}

// Body of POST /api/assets/{id}/holdings and PUT /api/holdings/{id}. Price is never sent —
// the backend derives it from amount and quantity.
export interface HoldingRequest {
    name: string;
    boughtForAmount: number;
    quantity: number;
    date: string;
    note: string | null;
}

// Aggregation over the filtered set, from GET /api/assets/{id}/holdings/summary. `averagePrice`
// is the weighted average (total cost / total quantity); it and the period are null when nothing
// matches. Mirrors the backend HoldingSummaryResponse.
export interface HoldingSummary {
    holdingCount: number;
    averagePrice: number | null;
    quantitySum: number;
    amountSum: number;
    periodStart: string | null;
    periodEnd: string | null;
}

// Client-side filter state for the holdings table. Empty strings mean "not applied"; the
// service omits them from the request so the backend treats them as null.
export interface HoldingFilter {
    name: string;
    from: string;
    to: string;
}


export const EMPTY_HOLDING_FILTER: HoldingFilter = {
    name: '',
    from: '',
    to: '',
};
