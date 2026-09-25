package com.peter_gerdzhikov.signal_flow_interest_topic_service.configurations;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.DatabaseSeedService;

import lombok.RequiredArgsConstructor;

/**
 * Seeds the initial category/interest-topic catalog on startup if none exists yet.
 */
@Component
@RequiredArgsConstructor
public class DatabaseLoader implements CommandLineRunner {

    private final DatabaseSeedService databaseSeedService;

    @Override
    public void run(String... args) {
        databaseSeedService.seedInitialCatalog();
    }
}
