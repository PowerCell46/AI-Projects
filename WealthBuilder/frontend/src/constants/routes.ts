// Application route paths, imported wherever navigation happens so no route string is
// duplicated across the codebase.

import { slugify } from '../utils/slug';


export const APP_ROUTES = {
    LOGIN: '/login',
    REGISTER: '/register',
    HOME: '/dashboard',
    // `:slug` segment is filled by buildAssetDetailPath when navigating. Asset names are unique
    // (case-insensitive), so the readable slug keys the route while the API stays id-based.
    ASSET_DETAIL: '/assets/:slug',
    ADMIN_ASSETS: '/admin/assets',
} as const;


export const buildAssetDetailPath = (assetName: string): string => `/assets/${slugify(assetName)}`;
