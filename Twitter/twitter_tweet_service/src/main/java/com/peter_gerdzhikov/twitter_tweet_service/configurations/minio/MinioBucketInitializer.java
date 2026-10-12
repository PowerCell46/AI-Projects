package com.peter_gerdzhikov.twitter_tweet_service.configurations.minio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.minio.MinioClient;

/**
 * Creates the tweet-images bucket, registered under this service's scan.
 */
@Component
public class MinioBucketInitializer
        extends com.peter_gerdzhikov.twitter_shared.storage.MinioBucketInitializer {

    public MinioBucketInitializer(@Value("${app.minio.bucket}") String bucket, MinioClient minioClient) {
        super(bucket, minioClient);
    }
}
