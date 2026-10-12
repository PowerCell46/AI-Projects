package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.minio.MinioClient;

/**
 * The minio-backed object store, registered under this service's scan.
 */
@Service
public class ObjectStorageServiceImpl
        extends com.peter_gerdzhikov.twitter_shared.storage.ObjectStorageServiceImpl {

    public ObjectStorageServiceImpl(@Value("${app.minio.bucket}") String bucket, MinioClient minioClient) {
        super(bucket, minioClient);
    }
}
