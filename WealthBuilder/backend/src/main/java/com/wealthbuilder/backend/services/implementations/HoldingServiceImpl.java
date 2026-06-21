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
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
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

        final List<AssetHolding> holdings = holdingRepository
                .search(user, asset, likePattern(filter.getName()), filter.getFrom(), filter.getTo(),
                        Pageable.unpaged())
                .getContent();

        return holdings.isEmpty() ? HoldingSummaryResponse.empty() : aggregate(holdings);
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
     * Computes the weighted average price (total spent / total units, the real cost basis),
     * the quantity/amount sums, and the purchase-date span over a non-empty holdings list.
     */
    private HoldingSummaryResponse aggregate(List<AssetHolding> holdings) {
        BigDecimal amountSum = BigDecimal.ZERO;
        BigDecimal quantitySum = BigDecimal.ZERO;

        for (final AssetHolding holding : holdings) {
            amountSum = amountSum.add(holding.getBoughtForAmount());
            quantitySum = quantitySum.add(holding.getQuantity());
        }

        final BigDecimal averagePrice = Money.unitPrice(amountSum, quantitySum);

        return HoldingSummaryResponse.of(
                holdings.size(),
                averagePrice,
                quantitySum,
                amountSum,
                earliestDate(holdings),
                latestDate(holdings));
    }

    private LocalDate earliestDate(List<AssetHolding> holdings) {
        return holdings
                .stream()
                .map(AssetHolding::getDate)
                .min(Comparator.naturalOrder())
                .orElseThrow();
    }

    private LocalDate latestDate(List<AssetHolding> holdings) {
        return holdings
                .stream()
                .map(AssetHolding::getDate)
                .max(Comparator.naturalOrder())
                .orElseThrow();
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
