package com.wealthbuilder.backend.services.interfaces;

import com.wealthbuilder.backend.DTOs.PageResponse;
import com.wealthbuilder.backend.DTOs.holding.HoldingFilter;
import com.wealthbuilder.backend.DTOs.holding.HoldingRequest;
import com.wealthbuilder.backend.DTOs.holding.HoldingResponse;
import com.wealthbuilder.backend.DTOs.holding.HoldingSummaryResponse;
import org.springframework.data.domain.Pageable;


/**
 * Manages a user's holdings within an asset. Every operation is scoped to the calling user:
 * reads only see the caller's holdings, and writes verify ownership before mutating.
 */
public interface HoldingService {

    /**
     * The caller's holdings for one asset, narrowed by {@code filter} and ordered newest
     * purchase first; 404 if the asset is unknown.
     */
    PageResponse<HoldingResponse> listHoldings(
            String username, Long assetId, HoldingFilter filter, Pageable pageable);

    /**
     * Aggregation over the caller's holdings for one asset, narrowed by the same {@code filter}
     * as the listing so the totals match whatever the table currently shows; 404 if the asset
     * is unknown.
     */
    HoldingSummaryResponse summarize(String username, Long assetId, HoldingFilter filter);

    /**
     * Records a holding owned by the caller; 404 if the asset is unknown.
     */
    HoldingResponse create(String username, Long assetId, HoldingRequest request);

    /**
     * Edits the caller's holding; 404 if unknown, 403 if owned by someone else.
     */
    HoldingResponse update(String username, Long holdingId, HoldingRequest request);

    /**
     * Deletes the caller's holding; 404 if unknown, 403 if owned by someone else.
     */
    void delete(String username, Long holdingId);
}
