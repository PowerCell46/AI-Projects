import { apiRequest } from './apiClient';
import { HOLDING_ENDPOINTS } from '../constants/api';
import type { Holding, HoldingRequest, HoldingSummary } from '../types/holding';
import type { PageResponse } from '../types/page';


// Maps the backend holding endpoints to typed calls. Every call is scoped server-side to the
// authenticated user; create is nested under an asset, edit/delete address a holding by id.

export const fetchHoldings = (
    assetId: number,
    page: number,
    size: number,
): Promise<PageResponse<Holding>> => {
    return apiRequest<PageResponse<Holding>>(HOLDING_ENDPOINTS.byAsset(assetId, page, size));
};

export const fetchHoldingSummary = (assetId: number): Promise<HoldingSummary> => {
    return apiRequest<HoldingSummary>(HOLDING_ENDPOINTS.summary(assetId));
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
