import { apiRequest } from './apiClient';
import { HOLDING_ENDPOINTS } from '../constants/api';
import type { Holding, HoldingFilter, HoldingRequest } from '../types/holding';
import type { PageResponse } from '../types/page';


// Maps the backend holding endpoints to typed calls. Every call is scoped server-side to the
// authenticated user; create is nested under an asset, edit/delete address a holding by id.

export const fetchHoldings = (
    assetId: number,
    page: number,
    size: number,
    filter: HoldingFilter,
): Promise<PageResponse<Holding>> => {
    return apiRequest<PageResponse<Holding>>(
        HOLDING_ENDPOINTS.byAsset(assetId, buildQuery(page, size, filter)),
    );
};

export const createHolding = (assetId: number, request: HoldingRequest): Promise<Holding> => {
    return apiRequest<Holding>(HOLDING_ENDPOINTS.create(assetId), { method: 'POST', body: request });
};

export const updateHolding = (id: number, request: HoldingRequest): Promise<Holding> => {
    return apiRequest<Holding>(HOLDING_ENDPOINTS.byId(id), { method: 'PUT', body: request });
};

export const deleteHolding = (id: number): Promise<void> => {
    return apiRequest<void>(HOLDING_ENDPOINTS.byId(id), { method: 'DELETE' });
};


/**
 * Serialises paging plus the set filters into a query string, omitting any filter the user
 * left blank so the backend applies only the criteria that are actually present.
 */
const buildQuery = (page: number, size: number, filter: HoldingFilter): string => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });

    if (filter.name.trim().length > 0) {
        params.set('name', filter.name.trim());
    }
    if (filter.from.length > 0) {
        params.set('from', filter.from);
    }
    if (filter.to.length > 0) {
        params.set('to', filter.to);
    }

    return params.toString();
};
