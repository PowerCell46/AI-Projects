package com.peter_gerdzhikov.twitter_tweet_service.configurations.minio;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Fails startup when MinIO is unreachable: an app that can't store tweet images should not report healthy.
 */
@Slf4j
@Component
public class MinioBucketInitializer implements ApplicationRunner {

    private final String bucket;

    private final MinioClient minioClient;

    public MinioBucketInitializer(@Value("${app.minio.bucket}") String bucket, MinioClient minioClient) {
        this.bucket = bucket;
        this.minioClient = minioClient;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        BucketExistsArgs existsArgs = BucketExistsArgs
                .builder()
                .bucket(bucket)
                .build();
        boolean exists = minioClient.bucketExists(existsArgs);
        if (exists) {
            return;
        }

        MakeBucketArgs makeArgs = MakeBucketArgs
                .builder()
                .bucket(bucket)
                .build();
        minioClient.makeBucket(makeArgs);
        log.info("Created MinIO bucket {}.", bucket);
    }
}
