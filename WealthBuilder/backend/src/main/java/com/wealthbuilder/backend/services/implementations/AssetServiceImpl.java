package com.wealthbuilder.backend.services.implementations;

import com.wealthbuilder.backend.DTOs.asset.AssetRequest;
import com.wealthbuilder.backend.DTOs.asset.AssetResponse;
import com.wealthbuilder.backend.entities.Asset;
import com.wealthbuilder.backend.exceptions.asset.AssetInUseException;
import com.wealthbuilder.backend.exceptions.asset.AssetNameAlreadyTakenException;
import com.wealthbuilder.backend.exceptions.asset.AssetNotFoundException;
import com.wealthbuilder.backend.repositories.AssetRepository;
import com.wealthbuilder.backend.repositories.HoldingRepository;
import com.wealthbuilder.backend.services.interfaces.AssetService;
import com.wealthbuilder.backend.utils.DataUriImage;
import com.wealthbuilder.backend.utils.Slug;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;


/**
 * Default {@link AssetService}. Name uniqueness is enforced case-insensitively before each
 * write so the catalog can't hold "Stocks" and "stocks" side by side.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetServiceImpl implements AssetService {

    private final AssetRepository assetRepository;

    private final HoldingRepository holdingRepository;

    // Newest first. The id is IDENTITY-generated, so descending id is creation order — the catalog
    // has no separate created-date column, and a monotonic id is an exact proxy for one.
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "id");

    @Override
    @Transactional(readOnly = true)
    public List<AssetResponse> findAll() {
        final Set<Long> referencedAssetIds = holdingRepository.findReferencedAssetIds();

        return assetRepository
                .findAll(NEWEST_FIRST)
                .stream()
                .map(asset -> AssetResponse.from(asset, referencedAssetIds.contains(asset.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AssetResponse findById(Long id) {
        final Asset asset = requireAsset(id);

        return AssetResponse.from(asset, holdingRepository.existsByAsset(asset));
    }

    /**
     * Resolves a name slug against the catalog. The catalog is small (asset *types*), so a scan
     * that slugifies each name in-memory is cheap and keeps the same slug rules as the frontend
     * without a stored slug column.
     */
    @Override
    @Transactional(readOnly = true)
    public AssetResponse findBySlug(String slug) {
        return assetRepository
                .findAll()
                .stream()
                .filter(asset -> Slug.of(asset.getName()).equals(slug))
                .findFirst()
                .map(asset -> AssetResponse.from(asset, holdingRepository.existsByAsset(asset)))
                .orElseThrow(() -> new AssetNotFoundException(slug));
    }

    @Override
    @Transactional(readOnly = true)
    public DataUriImage findImage(Long id) {
        return DataUriImage.parse(requireAsset(id).getImageBase64());
    }

    /**
     * The {@code existsByNameIgnoreCase} read gives a friendly error on the common path, but it
     * is not race-safe on its own: two concurrent creates can both pass it. The DB unique index
     * on the normalized name is the real guard — the flush below is what atomically rejects the
     * loser, which we translate back into the same conflict.
     */
    @Override
    @Transactional
    public AssetResponse create(AssetRequest request) {
        if (assetRepository.existsByNameIgnoreCase(request.getName())) {
            throw new AssetNameAlreadyTakenException(request.getName());
        }

        final Asset asset = new Asset(
                request.getName(),
                request.getDescription(),
                request.getImageBase64(),
                request.getImageName());
        saveEnforcingNameUniqueness(asset, request.getName());

        log.info("Created asset with name '{}' and id '{}'.", asset.getName(), asset.getId());

        return AssetResponse.from(asset, false);
    }

    @Override
    @Transactional
    public AssetResponse update(Long id, AssetRequest request) {
        final Asset asset = requireAsset(id);

        if (assetRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new AssetNameAlreadyTakenException(request.getName());
        }

        asset.setName(request.getName());
        asset.setDescription(request.getDescription());
        asset.setImageBase64(request.getImageBase64());
        asset.setImageName(request.getImageName());
        saveEnforcingNameUniqueness(asset, request.getName());

        log.info("Updated asset with id '{}'.", id);

        return AssetResponse.from(asset, holdingRepository.existsByAsset(asset));
    }

    /**
     * Refuses to delete an asset that any user still holds, so the {@code asset_id} foreign key is
     * never violated. The catalog exposes the same {@code inUse} flag up front, but the check is
     * repeated here to close the gap between listing the asset and confirming the delete.
     */
    @Override
    @Transactional
    public void delete(Long id) {
        final Asset asset = requireAsset(id);

        if (holdingRepository.existsByAsset(asset)) {
            throw new AssetInUseException(id);
        }

        assetRepository.delete(asset);

        log.info("Deleted asset with id '{}'.", id);
    }

    /**
     * Flushes immediately so the DB unique constraint on the normalized name is checked now,
     * inside this try, rather than silently at commit. A violation means a concurrent write won
     * the race, so we surface it as the same conflict the read check would have raised.
     */
    private void saveEnforcingNameUniqueness(Asset asset, String name) {
        try {
            assetRepository.saveAndFlush(asset);
        } catch (DataIntegrityViolationException ex) {
            throw new AssetNameAlreadyTakenException(name);
        }
    }

    private Asset requireAsset(Long id) {
        return assetRepository
                .findById(id)
                .orElseThrow(() -> new AssetNotFoundException(id));
    }
}
