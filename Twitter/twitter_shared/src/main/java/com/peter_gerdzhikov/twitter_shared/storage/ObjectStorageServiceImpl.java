package com.peter_gerdzhikov.twitter_shared.storage;

import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ObjectStorageServiceImpl implements ObjectStorageService {

    private static final long UNKNOWN_PART_SIZE = -1;

    private final String bucket;

    private final MinioClient minioClient;

    public ObjectStorageServiceImpl(@Value("${app.minio.bucket}") String bucket, MinioClient minioClient) {
        this.bucket = bucket;
        this.minioClient = minioClient;
    }

    @Override
    public void put(String objectKey, InputStream content, long sizeBytes, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs
                    .builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(content, sizeBytes, UNKNOWN_PART_SIZE)
                    .contentType(contentType)
                    .build());

        } catch (Exception ex) {
            throw unavailable("put", ex);
        }
    }

    @Override
    public InputStream get(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs
                    .builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());

        } catch (Exception ex) {
            throw unavailable("get", ex);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs
                    .builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());

        } catch (Exception ex) {
            throw unavailable("delete", ex);
        }
    }

    private StorageUnavailableException unavailable(String operation, Exception ex) {
        if (ex instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }

        log.error("MinIO {} failed.", operation, ex);
        return new StorageUnavailableException(ex);
    }
}
