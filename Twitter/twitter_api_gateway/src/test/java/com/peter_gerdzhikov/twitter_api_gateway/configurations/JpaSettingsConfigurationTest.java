package com.peter_gerdzhikov.twitter_api_gateway.configurations;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JpaSettingsConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void should_release_the_database_connection_before_the_response_is_rendered() {
        contextRunner.run(context -> assertThat(context.getEnvironment().getProperty("spring.jpa.open-in-view", Boolean.class))
                .isFalse());
    }
}
