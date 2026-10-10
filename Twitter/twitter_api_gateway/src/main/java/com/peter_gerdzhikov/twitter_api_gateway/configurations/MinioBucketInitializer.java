package com.peter_gerdzhikov.twitter_api_gateway.configurations;

import java.util.Set;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs first of all runners: the data seeder uploads pictures into the bucket this creates.
 * Fails startup when MinIO is unreachable: an app that can't store pictures should not report healthy. Two instances
 * starting together can both find the bucket missing; the one that loses the race carries on.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MinioBucketInitializer implements ApplicationRunner {

    private static final Set<String> BUCKET_CREATED_BY_ANOTHER_INSTANCE = Set.of("BucketAlreadyOwnedByYou", "BucketAlreadyExists");

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

        try {
            minioClient.makeBucket(makeArgs);
            log.info("Created MinIO bucket {}.", bucket);

        } catch (ErrorResponseException ex) {
            if (!BUCKET_CREATED_BY_ANOTHER_INSTANCE.contains(ex.errorResponse().code())) {
                throw ex;
            }

            log.info("MinIO bucket {} was created by another instance meanwhile.", bucket);
        }
    }
}
