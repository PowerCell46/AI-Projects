import { apiRequest } from './apiClient';
import { DASHBOARD_ENDPOINTS } from '../constants/api';
import type { AssetDistribution } from '../types/dashboard';


// Read-only home-screen aggregations for the signed-in user.

export const fetchDistribution = (): Promise<AssetDistribution[]> => {
    return apiRequest<AssetDistribution[]>(DASHBOARD_ENDPOINTS.DISTRIBUTION);
};
