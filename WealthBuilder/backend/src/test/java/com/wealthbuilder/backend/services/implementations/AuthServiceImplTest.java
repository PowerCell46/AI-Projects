package com.wealthbuilder.backend.services.implementations;

import com.wealthbuilder.backend.DTOs.auth.AuthResponse;
import com.wealthbuilder.backend.DTOs.auth.CurrentUserResponse;
import com.wealthbuilder.backend.DTOs.auth.LoginRequest;
import com.wealthbuilder.backend.DTOs.auth.RegisterRequest;
import com.wealthbuilder.backend.entities.Role;
import com.wealthbuilder.backend.entities.User;
import com.wealthbuilder.backend.exceptions.auth.UsernameAlreadyTakenException;
import com.wealthbuilder.backend.repositories.HoldingRepository;
import com.wealthbuilder.backend.repositories.UserRepository;
import com.wealthbuilder.backend.services.interfaces.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


/**
 * Unit test for the authentication service. All collaborators are mocked, so this verifies
 * orchestration and branching, not persistence or real token signing.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String USERNAME = "bob";

    private static final String RAW_PASSWORD = "raw-password";

    private static final String ENCODED_PASSWORD = "encoded-password";

    private static final String TOKEN = "signed.jwt.token";

    @Mock
    private UserRepository userRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        void should_SaveUserAndReturnToken_When_UsernameIsFree() {
            given(userRepository.existsByUsername(USERNAME)).willReturn(false);
            given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);
            given(jwtService.issueToken(USERNAME, Role.USER)).willReturn(TOKEN);

            final AuthResponse response = authService.register(registerRequest());

            assertThat(response.getToken()).isEqualTo(TOKEN);
        }

        @Test
        void should_PersistUserWithRoleUserAndEncodedPassword_When_Registering() {
            given(userRepository.existsByUsername(USERNAME)).willReturn(false);
            given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);
            given(jwtService.issueToken(USERNAME, Role.USER)).willReturn(TOKEN);

            authService.register(registerRequest());

            final ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(saved.capture());
            assertThat(saved.getValue().getUsername()).isEqualTo(USERNAME);
            assertThat(saved.getValue().getPasswordHash()).isEqualTo(ENCODED_PASSWORD);
            assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
        }

        @Test
        void should_ThrowAndNotSave_When_UsernameAlreadyTaken() {
            given(userRepository.existsByUsername(USERNAME)).willReturn(true);

            assertThatThrownBy(() -> authService.register(registerRequest()))
                    .isInstanceOf(UsernameAlreadyTakenException.class);

            verify(userRepository, never()).save(any());
            verify(jwtService, never()).issueToken(anyString(), any());
        }
    }

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        void should_AuthenticateAndReturnToken_When_CredentialsValid() {
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(existingUser(Role.USER)));
            given(jwtService.issueToken(USERNAME, Role.USER)).willReturn(TOKEN);

            final AuthResponse response = authService.login(loginRequest());

            assertThat(response.getToken()).isEqualTo(TOKEN);
            verify(authenticationManager).authenticate(
                    new UsernamePasswordAuthenticationToken(USERNAME, RAW_PASSWORD));
        }

        @Test
        void should_PropagateBadCredentials_When_AuthenticationFails() {
            given(authenticationManager.authenticate(any()))
                    .willThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.login(loginRequest()))
                    .isInstanceOf(BadCredentialsException.class);

            verify(jwtService, never()).issueToken(anyString(), any());
        }
    }

    @Nested
    @DisplayName("Current user")
    class CurrentUser {

        @Test
        void should_ReturnSnapshotWithSummedBalance_When_UserHasHoldings() {
            final User user = existingUser(Role.USER);
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
            given(holdingRepository.sumInvestedByUser(user)).willReturn(new BigDecimal("1250.5000"));

            final CurrentUserResponse response = authService.me(USERNAME);

            assertThat(response.getUsername()).isEqualTo(USERNAME);
            assertThat(response.getRole()).isEqualTo(Role.USER);
            assertThat(response.getBalance()).isEqualByComparingTo("1250.5000");
        }

        @Test
        void should_ReturnZeroBalance_When_UserHasNoHoldings() {
            final User user = existingUser(Role.USER);
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
            given(holdingRepository.sumInvestedByUser(user)).willReturn(BigDecimal.ZERO);

            final CurrentUserResponse response = authService.me(USERNAME);

            assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    private static RegisterRequest registerRequest() {
        final RegisterRequest request = new RegisterRequest();
        request.setUsername(USERNAME);
        request.setPassword(RAW_PASSWORD);

        return request;
    }

    private static LoginRequest loginRequest() {
        final LoginRequest request = new LoginRequest();
        request.setUsername(USERNAME);
        request.setPassword(RAW_PASSWORD);

        return request;
    }

    private static User existingUser(Role role) {
        return new User(USERNAME, ENCODED_PASSWORD, role);
    }
}
