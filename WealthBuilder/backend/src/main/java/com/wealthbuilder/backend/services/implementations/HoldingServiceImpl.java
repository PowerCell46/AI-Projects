package com.wealthbuilder.backend.services.implementations;

import com.wealthbuilder.backend.DTOs.PageResponse;
import com.wealthbuilder.backend.DTOs.holding.HoldingFilter;
import com.wealthbuilder.backend.DTOs.holding.HoldingRequest;
import com.wealthbuilder.backend.DTOs.holding.HoldingResponse;
import com.wealthbuilder.backend.DTOs.holding.HoldingSummaryResponse;
import com.wealthbuilder.backend.entities.Asset;
import com.wealthbuilder.backend.entities.AssetHolding;
import com.wealthbuilder.backend.entities.User;
import com.wealthbuilder.backend.exceptions.asset.AssetNotFoundException;
import com.wealthbuilder.backend.exceptions.holding.HoldingNotFoundException;
import com.wealthbuilder.backend.repositories.AssetRepository;
import com.wealthbuilder.backend.repositories.HoldingRepository;
import com.wealthbuilder.backend.repositories.UserRepository;
import com.wealthbuilder.backend.repositories.projections.HoldingAggregate;
import com.wealthbuilder.backend.services.interfaces.HoldingService;
import com.wealthbuilder.backend.utils.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;


/**
 * Default {@link HoldingService}. Listing is forced to a deterministic newest-first order
 * regardless of the incoming page request, and ownership is checked on every write.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HoldingServiceImpl implements HoldingService {

    private final HoldingRepository holdingRepository;

    private final AssetRepository assetRepository;

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<HoldingResponse> listHoldings(
            String username, Long assetId, HoldingFilter filter, Pageable pageable) {
        final User user = requireUser(username);
        final Asset asset = requireAsset(assetId);

        final Page<HoldingResponse> page = holdingRepository
                .search(user, asset, likePattern(filter.getName()), filter.getFrom(), filter.getTo(),
                        newestFirst(pageable))
                .map(HoldingResponse::from);

        return PageResponse.of(page);
    }

    /**
     * Turns a raw name fragment into a lowercased {@code %fragment%} LIKE pattern, or null when
     * there is no fragment so the repository skips the name criterion entirely.
     */
    private String likePattern(String name) {
        if (name == null) {
            return null;
        }

        return "%" + name.toLowerCase(Locale.ROOT) + "%";
    }

    @Override
    @Transactional(readOnly = true)
    public HoldingSummaryResponse summarize(String username, Long assetId, HoldingFilter filter) {
        final User user = requireUser(username);
        final Asset asset = requireAsset(assetId);

        final HoldingAggregate aggregate = holdingRepository
                .aggregate(user, asset, likePattern(filter.getName()), filter.getFrom(), filter.getTo());

        return toSummary(aggregate);
    }

    @Override
    @Transactional
    public HoldingResponse create(String username, Long assetId, HoldingRequest request) {
        final User user = requireUser(username);
        final Asset asset = requireAsset(assetId);

        final AssetHolding holding = new AssetHolding(
                asset,
                user,
                request.getName(),
                request.getBoughtForAmount(),
                request.getUnit(),
                request.getQuantity(),
                request.getDate(),
                request.getNote());
        holdingRepository.save(holding);

        log.info("User with username '{}' added a holding with id '{}' to asset with id '{}'.", username, holding.getId(), assetId);

        return HoldingResponse.from(holding);
    }

    @Override
    @Transactional
    public HoldingResponse update(String username, Long holdingId, HoldingRequest request) {
        final AssetHolding holding = requireOwnedHolding(username, holdingId);

        holding.setName(request.getName());
        holding.setBoughtForAmount(request.getBoughtForAmount());
        holding.setUnit(request.getUnit());
        holding.setQuantity(request.getQuantity());
        holding.setDate(request.getDate());
        holding.setNote(request.getNote());

        log.info("User with username '{}' updated holding with id '{}'.", username, holdingId);

        return HoldingResponse.from(holding);
    }

    @Override
    @Transactional
    public void delete(String username, Long holdingId) {
        final AssetHolding holding = requireOwnedHolding(username, holdingId);
        holdingRepository.delete(holding);

        log.info("User with username '{}' deleted holding with id '{}'.", username, holdingId);
    }

    /**
     * Turns the SQL aggregate into a summary, deriving the weighted average price (total spent /
     * total units, the real cost basis) the same way per-holding rows do. An empty match set
     * (count zero) has no price or period.
     */
    private HoldingSummaryResponse toSummary(HoldingAggregate aggregate) {
        if (aggregate.getHoldingCount() == 0) {
            return HoldingSummaryResponse.empty();
        }

        final BigDecimal averagePrice =
                Money.unitPrice(aggregate.getAmountSum(), aggregate.getQuantitySum());

        return HoldingSummaryResponse.of(
                aggregate.getHoldingCount(),
                averagePrice,
                aggregate.getQuantitySum(),
                aggregate.getAmountSum(),
                aggregate.getPeriodStart(),
                aggregate.getPeriodEnd());
    }

    /**
     * Forces newest-purchase-first ordering (date, then id as a tie-break) so paging is
     * stable no matter what sort the client passes.
     */
    private Pageable newestFirst(Pageable pageable) {
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "date", "id"));
    }

    private AssetHolding requireOwnedHolding(String username, Long holdingId) {
        final AssetHolding holding = holdingRepository
                .findById(holdingId)
                .orElseThrow(() -> new HoldingNotFoundException(holdingId));

        if (!holding.getUser().getUsername().equals(username)) {
            throw new AccessDeniedException("Holding is owned by another user.");
        }

        return holding;
    }

    private User requireUser(String username) {
        return userRepository
                .findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));
    }

    private Asset requireAsset(Long assetId) {
        return assetRepository
                .findById(assetId)
                .orElseThrow(() -> new AssetNotFoundException(assetId));
    }
}
