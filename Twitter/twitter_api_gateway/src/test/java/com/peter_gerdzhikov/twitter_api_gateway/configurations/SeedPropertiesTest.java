package com.peter_gerdzhikov.twitter_api_gateway.configurations;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SeedPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(Binding.class, SeedProperties.class);

    @Test
    void should_bind_each_real_persons_password_by_lowercase_username_when_seeding_is_on() {
        contextRunner
                .withPropertyValues(
                        "app.seed.enabled=true",
                        "app.seed.passwords.gabi=Gabi-0wn-password",
                        "app.seed.passwords.gosho=Gosho-0wn-password")
                .run(context -> assertThat(context.getBean(SeedProperties.class).getPasswords())
                        .containsEntry("gabi", "Gabi-0wn-password")
                        .containsEntry("gosho", "Gosho-0wn-password"));
    }

    @Test
    void should_not_exist_when_seeding_is_off() {
        contextRunner
                .withPropertyValues("app.seed.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(SeedProperties.class));
    }

    @Configuration
    @EnableConfigurationProperties
    static class Binding {
    }
}
