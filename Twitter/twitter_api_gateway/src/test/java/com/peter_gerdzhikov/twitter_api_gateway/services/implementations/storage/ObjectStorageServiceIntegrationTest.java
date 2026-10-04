package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.StorageUnavailableException;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.storage.ObjectStorageService;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;

import io.minio.MinioClient;
import io.minio.StatObjectArgs;

@SpringBootTest
@ActiveProfiles("test")
class ObjectStorageServiceIntegrationTest extends AbstractMinioIntegrationTest {

    private static final byte[] CONTENT = {1, 2, 3, 4, 5, (byte) 0xFF, 0, 42};

    private static final String UNREACHABLE_MINIO_URL = "http://localhost:1";

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private ObjectStorageService objectStorageService;

    @Nested
    class Put {

        @Test
        void should_store_the_exact_bytes_and_the_content_type_when_the_key_is_new() throws Exception {
            String key = newKey();

            putContent(key, "image/png");

            assertThat(readAll(key)).isEqualTo(CONTENT);
            assertThat(contentTypeOf(key)).isEqualTo("image/png");
        }

        @Test
        void should_replace_the_object_when_the_key_already_exists() throws Exception {
            String key = newKey();
            putContent(key, "image/png");

            objectStorageService.put(key, new ByteArrayInputStream(new byte[]{9}), 1, "image/jpeg");

            assertThat(readAll(key)).containsExactly(9);
            assertThat(contentTypeOf(key)).isEqualTo("image/jpeg");
        }

        @Test
        void should_throw_storage_unavailable_when_minio_is_unreachable() {
            ObjectStorageService unreachable = unreachableService();

            assertThrows(StorageUnavailableException.class, () -> putContent(unreachable, newKey()));
        }
    }

    @Nested
    class Get {

        @Test
        void should_throw_storage_unavailable_when_the_object_does_not_exist() {
            assertThrows(StorageUnavailableException.class, () -> objectStorageService.get(newKey()));
        }

        @Test
        void should_throw_storage_unavailable_when_minio_is_unreachable() {
            ObjectStorageService unreachable = unreachableService();

            assertThrows(StorageUnavailableException.class, () -> unreachable.get(newKey()));
        }
    }

    @Nested
    class Delete {

        @Test
        void should_remove_the_object_when_it_exists() {
            String key = newKey();
            putContent(key, "image/png");

            objectStorageService.delete(key);

            assertThrows(StorageUnavailableException.class, () -> objectStorageService.get(key));
        }

        @Test
        void should_not_touch_other_objects_when_one_is_deleted() throws Exception {
            String deleted = newKey();
            String kept = newKey();
            putContent(deleted, "image/png");
            putContent(kept, "image/png");

            objectStorageService.delete(deleted);

            assertThat(readAll(kept)).isEqualTo(CONTENT);
        }

        @Test
        void should_not_throw_when_the_object_does_not_exist() {
            assertDoesNotThrow(() -> objectStorageService.delete(newKey()));
        }

        @Test
        void should_throw_storage_unavailable_when_minio_is_unreachable() {
            ObjectStorageService unreachable = unreachableService();

            assertThrows(StorageUnavailableException.class, () -> unreachable.delete(newKey()));
        }
    }

    private String newKey() {
        return UUID.randomUUID().toString();
    }

    private void putContent(String key, String contentType) {
        objectStorageService.put(key, new ByteArrayInputStream(CONTENT), CONTENT.length, contentType);
    }

    private void putContent(ObjectStorageService service, String key) {
        service.put(key, new ByteArrayInputStream(CONTENT), CONTENT.length, "image/png");
    }

    private byte[] readAll(String key) throws Exception {
        try (InputStream stream = objectStorageService.get(key)) {
            return stream.readAllBytes();
        }
    }

    private String contentTypeOf(String key) throws Exception {
        return minioClient
                .statObject(StatObjectArgs.builder().bucket(TEST_BUCKET).object(key).build())
                .contentType();
    }

    private ObjectStorageService unreachableService() {
        MinioClient unreachableClient = MinioClient
                .builder()
                .endpoint(UNREACHABLE_MINIO_URL)
                .credentials("unused", "unused-secret")
                .build();

        return new ObjectStorageServiceImpl(TEST_BUCKET, unreachableClient);
    }
}
