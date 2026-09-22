package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.LoginRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.RegisterRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Role;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.User;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.DuplicateEmailException;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.InvalidCredentialsException;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "password123";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, passwordEncoder);
    }

    @Nested
    class Register {

        @Test
        void should_hash_the_password_and_assign_the_user_role() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed-password");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            User saved = authService.register(registerRequest(EMAIL, PASSWORD));

            assertThat(saved.getEmail()).isEqualTo(EMAIL);
            assertThat(saved.getPassword()).isEqualTo("hashed-password");
            assertThat(saved.getRole()).isEqualTo(Role.USER);
        }

        @Test
        void should_throw_duplicate_email_exception_when_the_email_already_exists() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> authService.register(registerRequest(EMAIL, PASSWORD)))
                    .isInstanceOf(DuplicateEmailException.class);
        }
    }

    @Nested
    class Login {

        @Test
        void should_return_the_user_for_valid_credentials() {
            User user = enabledUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, user.getPassword())).thenReturn(true);

            User loggedIn = authService.login(loginRequest(EMAIL, PASSWORD));

            assertThat(loggedIn).isEqualTo(user);
        }

        @Test
        void should_throw_when_the_password_does_not_match() {
            User user = enabledUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, user.getPassword())).thenReturn(false);

            assertThatThrownBy(() -> authService.login(loginRequest(EMAIL, PASSWORD)))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        void should_throw_when_the_user_is_disabled() {
            User user = enabledUser();
            user.setEnabled(false);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, user.getPassword())).thenReturn(true);

            assertThatThrownBy(() -> authService.login(loginRequest(EMAIL, PASSWORD)))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        void should_encode_a_dummy_password_and_throw_when_the_email_is_unknown() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(loginRequest(EMAIL, PASSWORD)))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(passwordEncoder).encode(PASSWORD);
            verify(passwordEncoder, never()).matches(any(), any());
        }
    }

    @Nested
    class EnsureAdminExists {

        @Test
        void should_create_an_admin_when_none_exists() {
            when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
            when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed-password");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            authService.ensureAdminExists(EMAIL, PASSWORD);

            verify(userRepository).save(argThat(user ->
                    user.getEmail().equals(EMAIL) && user.getPassword().equals("hashed-password") && user.getRole() == Role.ADMIN));
        }

        @Test
        void should_do_nothing_when_an_admin_already_exists() {
            when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

            authService.ensureAdminExists(EMAIL, PASSWORD);

            verify(userRepository, never()).save(any());
        }
    }

    private User enabledUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(EMAIL);
        user.setPassword("hashed-password");
        user.setRole(Role.USER);
        user.setEnabled(true);
        return user;
    }

    private RegisterRequestDTO registerRequest(String email, String password) {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private LoginRequestDTO loginRequest(String email, String password) {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
