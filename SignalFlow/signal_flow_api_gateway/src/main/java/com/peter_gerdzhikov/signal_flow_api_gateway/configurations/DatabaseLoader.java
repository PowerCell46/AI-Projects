package com.peter_gerdzhikov.signal_flow_api_gateway.configurations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.AuthService;

import lombok.extern.slf4j.Slf4j;

/**
 * Seeds the first admin on startup if none exists yet. {@code app.admin.email}/{@code app.admin.password}
 * are optional - left unset (the default), seeding is skipped, so this stays a no-op until an operator
 * configures it (see DECISIONS.md's "Post-step-9" entry #4).
 */
@Slf4j
@Component
public class DatabaseLoader implements CommandLineRunner {

    private final String adminEmail;

    private final String adminPassword;

    private final AuthService authService;

    public DatabaseLoader(
            @Value("${app.admin.email}") String adminEmail,
            @Value("${app.admin.password}") String adminPassword,
            AuthService authService
    ) {
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.authService = authService;
    }

    @Override
    public void run(String... args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("app.admin.email/app.admin.password not set - skipping initial admin seeding.");
            return;
        }

        authService.ensureAdminExists(adminEmail, adminPassword);
    }
}
