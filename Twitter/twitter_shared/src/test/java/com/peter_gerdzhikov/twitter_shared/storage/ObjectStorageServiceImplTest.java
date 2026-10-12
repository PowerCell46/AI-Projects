package com.peter_gerdzhikov.twitter_shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

@ExtendWith(MockitoExtension.class)
class ObjectStorageServiceImplTest {

    private static final String BUCKET = "pictures";

    private static final String OBJECT_KEY = "some-key";

    @Mock
    private MinioClient minioClient;

    private ObjectStorageServiceImpl objectStorageService;

    @BeforeEach
    void setUp() {
        objectStorageService = new ObjectStorageServiceImpl(BUCKET, minioClient);
    }

    @Test
    void should_put_the_object_with_its_key_size_and_content_type() throws Exception {
        byte[] content = "bytes".getBytes(StandardCharsets.UTF_8);

        objectStorageService.put(OBJECT_KEY, new ByteArrayInputStream(content), content.length, "image/png");

        ArgumentCaptor<PutObjectArgs> args = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(args.capture());
        assertThat(args.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(args.getValue().object()).isEqualTo(OBJECT_KEY);
        assertThat(args.getValue().contentType().toString()).isEqualTo("image/png");
    }

    @Test
    void should_return_the_stored_bytes_on_get() throws Exception {
        byte[] content = "bytes".getBytes(StandardCharsets.UTF_8);
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenReturn(new GetObjectResponse(null, BUCKET, null, OBJECT_KEY, new ByteArrayInputStream(content)));

        byte[] read = objectStorageService.get(OBJECT_KEY).readAllBytes();

        assertThat(read).isEqualTo(content);
        ArgumentCaptor<GetObjectArgs> args = ArgumentCaptor.forClass(GetObjectArgs.class);
        verify(minioClient).getObject(args.capture());
        assertThat(args.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(args.getValue().object()).isEqualTo(OBJECT_KEY);
    }

    @Test
    void should_delete_the_object_on_delete() throws Exception {
        objectStorageService.delete(OBJECT_KEY);

        ArgumentCaptor<RemoveObjectArgs> args = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(args.capture());
        assertThat(args.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(args.getValue().object()).isEqualTo(OBJECT_KEY);
    }

    @Test
    void should_map_a_put_failure_to_storage_unavailable() throws Exception {
        ErrorResponseException failure = errorWithCode("AccessDenied");
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(failure);

        assertThatThrownBy(() -> objectStorageService.put(OBJECT_KEY, new ByteArrayInputStream(new byte[1]), 1, "image/png"))
                .isInstanceOf(StorageUnavailableException.class)
                .hasCause(failure);
    }

    @Test
    void should_map_a_get_failure_to_storage_unavailable() throws Exception {
        ErrorResponseException failure = errorWithCode("AccessDenied");
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(failure);

        assertThatThrownBy(() -> objectStorageService.get(OBJECT_KEY))
                .isInstanceOf(StorageUnavailableException.class)
                .hasCause(failure);
    }

    @Test
    void should_map_a_delete_failure_to_storage_unavailable() throws Exception {
        ErrorResponseException failure = errorWithCode("AccessDenied");
        doThrow(failure).when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThatThrownBy(() -> objectStorageService.delete(OBJECT_KEY))
                .isInstanceOf(StorageUnavailableException.class)
                .hasCause(failure);
    }

    private ErrorResponseException errorWithCode(String code) {
        Response response = new Response.Builder()
                .request(new Request.Builder().url("http://minio:9000/" + BUCKET).build())
                .protocol(Protocol.HTTP_1_1)
                .code(403)
                .message("Forbidden")
                .build();

        return new ErrorResponseException(
                new ErrorResponse(code, "message", BUCKET, null, null, null, null), response, null);
    }
}
