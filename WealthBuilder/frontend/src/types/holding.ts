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

// Aggregation over all of the user's holdings for one asset. The price/period fields are
// null when the user has no holdings yet.
export interface HoldingSummary {
    holdingCount: number;
    averagePrice: number | null;
    quantitySum: number;
    amountSum: number;
    periodStart: string | null;
    periodEnd: string | null;
}
