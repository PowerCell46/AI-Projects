package com.wealthbuilder.backend.services.implementations;

import com.wealthbuilder.backend.DTOs.asset.AssetRequest;
import com.wealthbuilder.backend.DTOs.asset.AssetResponse;
import com.wealthbuilder.backend.entities.Asset;
import com.wealthbuilder.backend.exceptions.asset.AssetNameAlreadyTakenException;
import com.wealthbuilder.backend.exceptions.asset.AssetNotFoundException;
import com.wealthbuilder.backend.repositories.AssetRepository;
import com.wealthbuilder.backend.services.interfaces.AssetService;
import com.wealthbuilder.backend.utils.DataUriImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * Default {@link AssetService}. Name uniqueness is enforced case-insensitively before each
 * write so the catalog can't hold "Stocks" and "stocks" side by side.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetServiceImpl implements AssetService {

    private final AssetRepository assetRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AssetResponse> findAll() {
        return assetRepository
                .findAll()
                .stream()
                .map(AssetResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AssetResponse findById(Long id) {
        return AssetResponse.from(requireAsset(id));
    }

    @Override
    @Transactional(readOnly = true)
    public DataUriImage findImage(Long id) {
        return DataUriImage.parse(requireAsset(id).getImageBase64());
    }

    /**
     * The duplicate check and insert share one transaction so two concurrent creates with the
     * same name can't both pass the check before either commits.
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
        assetRepository.save(asset);

        log.info("Created asset with name '{}' and id '{}'.", asset.getName(), asset.getId());

        return AssetResponse.from(asset);
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

        log.info("Updated asset id={}", id);

        return AssetResponse.from(asset);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        final Asset asset = requireAsset(id);
        assetRepository.delete(asset);

        log.info("Deleted asset id={}", id);
    }

    private Asset requireAsset(Long id) {
        return assetRepository
                .findById(id)
                .orElseThrow(() -> new AssetNotFoundException(id));
    }
}
