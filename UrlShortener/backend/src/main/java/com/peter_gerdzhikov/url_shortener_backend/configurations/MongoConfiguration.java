package com.peter_gerdzhikov.url_shortener_backend.configurations;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Switches on auditing so {@code CommonEntity}'s createdAt/updatedAt are populated on save.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfiguration {
}
