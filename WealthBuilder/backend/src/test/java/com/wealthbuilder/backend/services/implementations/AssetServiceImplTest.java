package com.wealthbuilder.backend.services.implementations;

import com.wealthbuilder.backend.DTOs.asset.AssetRequest;
import com.wealthbuilder.backend.DTOs.asset.AssetResponse;
import com.wealthbuilder.backend.entities.Asset;
import com.wealthbuilder.backend.exceptions.asset.AssetNameAlreadyTakenException;
import com.wealthbuilder.backend.exceptions.asset.AssetNotFoundException;
import com.wealthbuilder.backend.repositories.AssetRepository;
import com.wealthbuilder.backend.utils.DataUriImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


/**
 * Unit test for the asset service. The repository is mocked, so this verifies orchestration,
 * uniqueness branching and not-found handling rather than persistence behaviour.
 */
@ExtendWith(MockitoExtension.class)
class AssetServiceImplTest {

    private static final Long ASSET_ID = 7L;

    private static final String NAME = "Stocks";

    private static final String DESCRIPTION = "Equity instruments traded on public markets.";

    private static final String IMAGE = "data:image/png;base64,aGVsbG8=";

    private static final String IMAGE_NAME = "stocks.png";

    @Mock
    private AssetRepository assetRepository;

    @InjectMocks
    private AssetServiceImpl assetService;

    @Nested
    @DisplayName("Find all")
    class FindAll {

        @Test
        void should_ReturnMappedResponsesWithoutImage_When_AssetsExist() {
            given(assetRepository.findAll()).willReturn(List.of(existingAsset()));

            final List<AssetResponse> responses = assetService.findAll();

            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().getId()).isEqualTo(ASSET_ID);
            assertThat(responses.getFirst().getName()).isEqualTo(NAME);
            assertThat(responses.getFirst().getDescription()).isEqualTo(DESCRIPTION);
        }

        @Test
        void should_ReturnEmptyList_When_NoAssetsExist() {
            given(assetRepository.findAll()).willReturn(List.of());

            assertThat(assetService.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Find by id")
    class FindById {

        @Test
        void should_ReturnResponse_When_AssetExists() {
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.of(existingAsset()));

            final AssetResponse response = assetService.findById(ASSET_ID);

            assertThat(response.getId()).isEqualTo(ASSET_ID);
            assertThat(response.getName()).isEqualTo(NAME);
        }

        @Test
        void should_ThrowNotFound_When_AssetMissing() {
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> assetService.findById(ASSET_ID))
                    .isInstanceOf(AssetNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Find image")
    class FindImage {

        @Test
        void should_ReturnDecodedImage_When_AssetExists() {
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.of(existingAsset()));

            final DataUriImage image = assetService.findImage(ASSET_ID);

            assertThat(image.getMediaType()).isEqualTo(MediaType.IMAGE_PNG);
            assertThat(image.getBytes()).isNotEmpty();
        }

        @Test
        void should_ThrowNotFound_When_AssetMissing() {
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> assetService.findImage(ASSET_ID))
                    .isInstanceOf(AssetNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        void should_SaveAndReturnResponse_When_NameIsFree() {
            given(assetRepository.existsByNameIgnoreCase(NAME)).willReturn(false);

            final AssetResponse response = assetService.create(request(NAME));

            assertThat(response.getName()).isEqualTo(NAME);
            final ArgumentCaptor<Asset> saved = ArgumentCaptor.forClass(Asset.class);
            verify(assetRepository).save(saved.capture());
            assertThat(saved.getValue().getName()).isEqualTo(NAME);
            assertThat(saved.getValue().getDescription()).isEqualTo(DESCRIPTION);
            assertThat(saved.getValue().getImageBase64()).isEqualTo(IMAGE);
            assertThat(saved.getValue().getImageName()).isEqualTo(IMAGE_NAME);
        }

        @Test
        void should_ThrowConflictAndNotSave_When_NameAlreadyTakenCaseInsensitively() {
            given(assetRepository.existsByNameIgnoreCase(NAME)).willReturn(true);

            assertThatThrownBy(() -> assetService.create(request(NAME)))
                    .isInstanceOf(AssetNameAlreadyTakenException.class);

            verify(assetRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Update")
    class Update {

        @Test
        void should_MutateAssetFields_When_NameDoesNotCollide() {
            final Asset asset = existingAsset();
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.of(asset));
            given(assetRepository.existsByNameIgnoreCaseAndIdNot("Crypto", ASSET_ID)).willReturn(false);

            final AssetResponse response = assetService.update(ASSET_ID, request("Crypto"));

            assertThat(response.getName()).isEqualTo("Crypto");
            assertThat(asset.getName()).isEqualTo("Crypto");
            assertThat(asset.getDescription()).isEqualTo(DESCRIPTION);
        }

        // The id-excluding query means renaming an asset to its own (case-variant) name is
        // not a conflict, so saving the same record under a different case must succeed.
        @Test
        void should_Succeed_When_NameMatchesOnlyTheSameAssetBeingUpdated() {
            final Asset asset = existingAsset();
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.of(asset));
            given(assetRepository.existsByNameIgnoreCaseAndIdNot("stocks", ASSET_ID)).willReturn(false);

            final AssetResponse response = assetService.update(ASSET_ID, request("stocks"));

            assertThat(response.getName()).isEqualTo("stocks");
        }

        @Test
        void should_ThrowConflict_When_NameTakenByAnotherAsset() {
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.of(existingAsset()));
            given(assetRepository.existsByNameIgnoreCaseAndIdNot("Crypto", ASSET_ID)).willReturn(true);

            assertThatThrownBy(() -> assetService.update(ASSET_ID, request("Crypto")))
                    .isInstanceOf(AssetNameAlreadyTakenException.class);
        }

        @Test
        void should_ThrowNotFound_When_AssetMissing() {
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> assetService.update(ASSET_ID, request(NAME)))
                    .isInstanceOf(AssetNotFoundException.class);

            verify(assetRepository, never()).existsByNameIgnoreCaseAndIdNot(any(), any());
        }
    }

    @Nested
    @DisplayName("Delete")
    class Delete {

        @Test
        void should_DeleteAsset_When_AssetExists() {
            final Asset asset = existingAsset();
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.of(asset));

            assetService.delete(ASSET_ID);

            verify(assetRepository).delete(asset);
        }

        @Test
        void should_ThrowNotFoundAndNotDelete_When_AssetMissing() {
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> assetService.delete(ASSET_ID))
                    .isInstanceOf(AssetNotFoundException.class);

            verify(assetRepository, never()).delete(any());
        }
    }

    private static Asset existingAsset() {
        final Asset asset = new Asset(NAME, DESCRIPTION, IMAGE, IMAGE_NAME);
        asset.setId(ASSET_ID);

        return asset;
    }

    private static AssetRequest request(String name) {
        final AssetRequest request = new AssetRequest();
        request.setName(name);
        request.setDescription(DESCRIPTION);
        request.setImageBase64(IMAGE);
        request.setImageName(IMAGE_NAME);

        return request;
    }
}
