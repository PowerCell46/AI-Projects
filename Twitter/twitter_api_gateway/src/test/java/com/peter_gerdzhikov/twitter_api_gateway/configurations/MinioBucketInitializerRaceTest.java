package com.peter_gerdzhikov.twitter_api_gateway.configurations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;

@ExtendWith(MockitoExtension.class)
class MinioBucketInitializerRaceTest {

    @Mock
    private MinioClient minioClient;

    private MinioBucketInitializer initializer;

    @BeforeEach
    void setUp() throws Exception {
        initializer = new MinioBucketInitializer("pictures", minioClient);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
    }

    @Test
    void should_carry_on_when_another_instance_created_the_bucket_first() throws Exception {
        doThrow(errorWithCode("BucketAlreadyOwnedByYou")).when(minioClient).makeBucket(any(MakeBucketArgs.class));

        assertDoesNotThrow(() -> initializer.run(new DefaultApplicationArguments()));
    }

    @Test
    void should_still_fail_when_creating_the_bucket_fails_for_another_reason() throws Exception {
        doThrow(errorWithCode("AccessDenied")).when(minioClient).makeBucket(any(MakeBucketArgs.class));

        assertThrows(ErrorResponseException.class, () -> initializer.run(new DefaultApplicationArguments()));
    }

    private ErrorResponseException errorWithCode(String code) {
        return new ErrorResponseException(new ErrorResponse(code, "message", "pictures", null, null, null, null), null, null);
    }
}
