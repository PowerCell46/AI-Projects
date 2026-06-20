// Application route paths, imported wherever navigation happens so no route string is
// duplicated across the codebase.

export const APP_ROUTES = {
    LOGIN: '/login',
    REGISTER: '/register',
    HOME: '/',
    // `:name` segment is filled by buildAssetDetailPath when navigating. Asset names are unique
    // (case-insensitive), so the readable name keys the route while the API stays id-based.
    ASSET_DETAIL: '/assets/:name',
    ADMIN_ASSETS: '/admin/assets',
} as const;


export const buildAssetDetailPath = (assetName: string): string =>
    `/assets/${encodeURIComponent(assetName)}`;
