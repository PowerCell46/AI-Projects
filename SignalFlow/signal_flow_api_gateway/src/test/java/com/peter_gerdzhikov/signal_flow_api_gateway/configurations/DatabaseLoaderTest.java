package com.peter_gerdzhikov.signal_flow_api_gateway.configurations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.AuthService;

@ExtendWith(MockitoExtension.class)
class DatabaseLoaderTest {

    private static final String EMAIL = "admin@example.com";
    private static final String PASSWORD = "password123";

    @Mock
    private AuthService authService;

    @Test
    void should_seed_the_admin_when_both_credentials_are_configured() throws Exception {
        DatabaseLoader databaseLoader = new DatabaseLoader(EMAIL, PASSWORD, authService);

        databaseLoader.run();

        verify(authService).ensureAdminExists(EMAIL, PASSWORD);
    }

    @Test
    void should_skip_seeding_when_the_email_is_blank() throws Exception {
        DatabaseLoader databaseLoader = new DatabaseLoader("", PASSWORD, authService);

        databaseLoader.run();

        verify(authService, never()).ensureAdminExists(any(), any());
    }

    @Test
    void should_skip_seeding_when_the_password_is_blank() throws Exception {
        DatabaseLoader databaseLoader = new DatabaseLoader(EMAIL, "", authService);

        databaseLoader.run();

        verify(authService, never()).ensureAdminExists(any(), any());
    }
}
