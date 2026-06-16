package com.wealthbuilder.backend.config;

import com.wealthbuilder.backend.entities.Role;
import com.wealthbuilder.backend.entities.User;
import com.wealthbuilder.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


/**
 * Seeds a single {@link Role#MODERATOR} from configuration at startup, but only when no
 * moderator exists yet — so restarts are idempotent and never clobber an existing one.
 * Registration always creates regular users, so this is the only path to a moderator.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final AppProperties appProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.MODERATOR)) {
            return;
        }

        final AppProperties.Moderator moderator = appProperties.moderator();
        if (isBlank(moderator.password())) {
            log.warn("No moderator exists and the seed password is blank; skipping moderator seeding.");
            return;
        }

        userRepository.save(new User(
                moderator.username(),
                passwordEncoder.encode(moderator.password()),
                Role.MODERATOR));
        log.info("Seeded moderator account '{}'.", moderator.username());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
