// Body of GET /api/dashboard/distribution — one slice per asset the current user has
// invested in. `amountInvested` is the summed boughtForAmount across the user's holdings.

export interface AssetDistribution {
    assetId: number;
    assetName: string;
    amountInvested: number;
}
