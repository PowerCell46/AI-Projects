package com.wealthbuilder.backend.services.interfaces;

import com.wealthbuilder.backend.dtos.dashboard.AssetDistributionResponse;

import java.util.List;


/**
 * Read-only dashboard aggregations for the authenticated user's home screen.
 */
public interface DashboardService {

    /**
     * Net invested per asset for the donut, highest first; assets the user holds nothing in
     * are omitted.
     */
    List<AssetDistributionResponse> distribution(String username);
}
