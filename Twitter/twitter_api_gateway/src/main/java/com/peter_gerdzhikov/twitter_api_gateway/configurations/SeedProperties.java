package com.peter_gerdzhikov.twitter_api_gateway.configurations;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * The passwords of the demo users that are real people, by lowercase username. Present only while seeding is on, so
 * a deployment that never seeds needs none of them.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.seed")
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class SeedProperties {

    private Map<String, String> passwords = new HashMap<>();
}
