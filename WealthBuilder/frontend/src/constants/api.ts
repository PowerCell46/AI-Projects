// Single source of truth for every backend URL. Components and services import these
// constants instead of hardcoding paths, so a base-URL change touches only this file.

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';


export const AUTH_ENDPOINTS = {
    REGISTER: `${API_BASE_URL}/auth/register`,
    LOGIN: `${API_BASE_URL}/auth/login`,
    ME: `${API_BASE_URL}/auth/me`,
} as const;

export const ASSET_ENDPOINTS = {
    LIST: `${API_BASE_URL}/assets`,
    byId: (id: number): string => `${API_BASE_URL}/assets/${id}`,
    image: (id: number): string => `${API_BASE_URL}/assets/${id}/image`,
} as const;

export const HOLDING_ENDPOINTS = {
    byAsset: (assetId: number, page: number, size: number): string =>
        `${API_BASE_URL}/assets/${assetId}/holdings?page=${page}&size=${size}`,
    summary: (assetId: number): string => `${API_BASE_URL}/assets/${assetId}/holdings/summary`,
    create: (assetId: number): string => `${API_BASE_URL}/assets/${assetId}/holdings`,
    byId: (id: number): string => `${API_BASE_URL}/holdings/${id}`,
} as const;

export { API_BASE_URL };
