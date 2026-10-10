package com.peter_gerdzhikov.twitter_tweet_service.configurations.minio;

import java.time.Duration;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfiguration {

    @Bean
    public MinioClient minioClient(
            @Value("${app.minio.url}") String url,
            @Value("${app.minio.access-key}") String accessKey,
            @Value("${app.minio.secret-key}") String secretKey,
            @Value("${app.minio.connect-timeout}") Duration connectTimeout,
            @Value("${app.minio.io-timeout}") Duration ioTimeout
    ) {
        MinioClient client = MinioClient
                .builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();

        client.setTimeout(connectTimeout.toMillis(), ioTimeout.toMillis(), ioTimeout.toMillis());

        return client;
    }
}
