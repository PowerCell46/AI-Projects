package com.peter_gerdzhikov.signal_flow_interest_topic_service.configurations;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.DatabaseSeedService;

@ExtendWith(MockitoExtension.class)
class DatabaseLoaderTest {

    @Mock
    private DatabaseSeedService databaseSeedService;

    private DatabaseLoader databaseLoader;

    @BeforeEach
    void setUp() {
        databaseLoader = new DatabaseLoader(databaseSeedService);
    }

    @Test
    void should_seed_the_initial_catalog_on_startup() {
        databaseLoader.run();

        verify(databaseSeedService).seedInitialCatalog();
    }
}
