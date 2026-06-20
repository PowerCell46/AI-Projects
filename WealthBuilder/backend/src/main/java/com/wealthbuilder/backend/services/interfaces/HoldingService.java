package com.wealthbuilder.backend.services.interfaces;

import com.wealthbuilder.backend.dtos.PageResponse;
import com.wealthbuilder.backend.dtos.holding.HoldingRequest;
import com.wealthbuilder.backend.dtos.holding.HoldingResponse;
import com.wealthbuilder.backend.dtos.holding.HoldingSummaryResponse;
import org.springframework.data.domain.Pageable;


/**
 * Manages a user's holdings within an asset. Every operation is scoped to the calling user:
 * reads only see the caller's holdings, and writes verify ownership before mutating.
 */
public interface HoldingService {

    /**
     * The caller's holdings for one asset, newest purchase first; 404 if the asset is unknown.
     */
    PageResponse<HoldingResponse> listHoldings(String username, Long assetId, Pageable pageable);

    /**
     * Aggregation over all of the caller's holdings for one asset; 404 if the asset is unknown.
     */
    HoldingSummaryResponse summarize(String username, Long assetId);

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
