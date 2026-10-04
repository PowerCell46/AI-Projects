package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.files.FileContentResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.files.DbFile;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.FileNotFoundException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.StorageUnavailableException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.DbFileRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.storage.ObjectStorageService;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;

@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    private static final UUID FILE_ID = UUID.randomUUID();

    private FileServiceImpl fileService;

    @Mock
    private DbFileRepository dbFileRepository;

    @Mock
    private ObjectStorageService objectStorageService;

    @BeforeEach
    void setUp() {
        fileService = new FileServiceImpl(dbFileRepository, objectStorageService);
    }

    @Nested
    class Open {

        @Test
        void should_return_the_stored_type_size_and_the_object_stream_when_the_file_exists() {
            DbFile file = TestEntities.newDbFile();
            file.setContentType("image/webp");
            file.setSizeBytes(77);
            InputStream stream = new ByteArrayInputStream(new byte[]{1});
            when(dbFileRepository.findById(FILE_ID)).thenReturn(Optional.of(file));
            when(objectStorageService.get(file.getObjectKey())).thenReturn(stream);

            FileContentResponseDTO content = fileService.open(FILE_ID);

            assertThat(content.getContentType()).isEqualTo("image/webp");
            assertThat(content.getSizeBytes()).isEqualTo(77);
            assertThat(content.getContent()).isSameAs(stream);
        }

        @Test
        void should_throw_without_touching_storage_when_the_id_is_unknown() {
            when(dbFileRepository.findById(FILE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fileService.open(FILE_ID)).isInstanceOf(FileNotFoundException.class);

            verifyNoInteractions(objectStorageService);
        }

        @Test
        void should_propagate_storage_unavailable_when_the_object_cannot_be_read() {
            DbFile file = TestEntities.newDbFile();
            when(dbFileRepository.findById(FILE_ID)).thenReturn(Optional.of(file));
            when(objectStorageService.get(file.getObjectKey()))
                    .thenThrow(new StorageUnavailableException(new RuntimeException("down")));

            assertThatThrownBy(() -> fileService.open(FILE_ID)).isInstanceOf(StorageUnavailableException.class);
        }
    }
}
