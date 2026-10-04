package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.storage;

import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.StorageUnavailableException;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.storage.ObjectStorageService;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
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

        } catch (Exception e) {
            throw unavailable("put", e);
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

        } catch (Exception e) {
            throw unavailable("get", e);
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

        } catch (Exception e) {
            throw unavailable("delete", e);
        }
    }

    private StorageUnavailableException unavailable(String operation, Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }

        log.error("MinIO {} failed.", operation, e);
        return new StorageUnavailableException(e);
    }
}
