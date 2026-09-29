package com.peter_gerdzhikov.twitter_api_gateway.configurations;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.unit.DataSize;

import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;

@SpringBootTest
@ActiveProfiles("test")
class UploadLimitsConfigurationTest extends AbstractMinioIntegrationTest {

    @Value("${app.upload.max-file-bytes}")
    private long maxFileBytes;

    @Value("${app.request.max-body-bytes}")
    private long maxBodyBytes;

    @Value("${app.request.max-upload-body-bytes}")
    private long maxUploadBodyBytes;

    @Autowired
    private Environment environment;

    @Test
    void should_cap_a_multipart_file_at_the_upload_file_limit() {
        DataSize springLimit = environment.getProperty("spring.servlet.multipart.max-file-size", DataSize.class);

        assertThat(springLimit.toBytes()).isEqualTo(maxFileBytes);
    }

    @Test
    void should_cap_a_multipart_request_at_the_upload_body_limit() {
        DataSize springLimit = environment.getProperty("spring.servlet.multipart.max-request-size", DataSize.class);

        assertThat(springLimit.toBytes()).isEqualTo(maxUploadBodyBytes);
    }

    @Test
    void should_leave_room_for_multipart_framing_around_a_maximum_size_file() {
        assertThat(maxUploadBodyBytes).isGreaterThan(maxFileBytes);
    }

    @Test
    void should_keep_the_upload_body_cap_above_the_normal_body_cap() {
        assertThat(maxUploadBodyBytes).isGreaterThan(maxBodyBytes);
    }
}
