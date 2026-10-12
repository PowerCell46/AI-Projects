package com.peter_gerdzhikov.twitter_api_gateway.configurations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.minio.MinioClient;

/**
 * Runs first of all runners: the data seeder uploads pictures into the bucket this creates.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MinioBucketInitializer
        extends com.peter_gerdzhikov.twitter_shared.storage.MinioBucketInitializer {

    public MinioBucketInitializer(@Value("${app.minio.bucket}") String bucket, MinioClient minioClient) {
        super(bucket, minioClient);
    }
}
