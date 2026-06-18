package com.wealthbuilder.backend.config;

import com.wealthbuilder.backend.entities.Role;
import com.wealthbuilder.backend.entities.User;
import com.wealthbuilder.backend.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


/**
 * Unit test for the startup moderator seeder. The repository, encoder and configuration
 * are mocked; only the seeding decision logic is exercised.
 */
@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    private static final String MOD_USERNAME = "moderator";

    private static final String MOD_PASSWORD = "seed-password";

    private static final String ENCODED_PASSWORD = "encoded-seed-password";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationArguments applicationArguments;

    @Test
    void should_DoNothing_When_ModeratorAlreadyExists() {
        given(userRepository.existsByRole(Role.MODERATOR)).willReturn(true);

        seederWith(MOD_PASSWORD).run(applicationArguments);

        verify(userRepository, never()).save(any());
    }

    @Test
    void should_SkipWithoutError_When_NoModeratorAndPasswordBlank() {
        given(userRepository.existsByRole(Role.MODERATOR)).willReturn(false);
        final DataSeeder seeder = seederWith("   ");

        assertThatCode(() -> seeder.run(applicationArguments)).doesNotThrowAnyException();

        verify(userRepository, never()).save(any());
    }

    @Test
    void should_SeedModeratorWithEncodedPassword_When_NoneExistAndPasswordValid() {
        given(userRepository.existsByRole(Role.MODERATOR)).willReturn(false);
        given(passwordEncoder.encode(MOD_PASSWORD)).willReturn(ENCODED_PASSWORD);

        seederWith(MOD_PASSWORD).run(applicationArguments);

        final ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo(MOD_USERNAME);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo(ENCODED_PASSWORD);
        assertThat(saved.getValue().getRole()).isEqualTo(Role.MODERATOR);
    }

    private DataSeeder seederWith(String moderatorPassword) {
        final AppProperties.Jwt jwt =
                new AppProperties.Jwt("any-secret-value-for-binding", Duration.ofMinutes(30));
        final AppProperties.Moderator moderator =
                new AppProperties.Moderator(MOD_USERNAME, moderatorPassword);
        final AppProperties appProperties =
                new AppProperties(java.util.List.of("http://localhost"), jwt, moderator);

        return new DataSeeder(userRepository, passwordEncoder, appProperties);
    }
}
