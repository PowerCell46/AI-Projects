package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.LoginRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.RegisterRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth.DuplicateEmailException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth.DuplicateUsernameException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth.EmailNotConfirmedException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth.InvalidCredentialsException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth.ConfirmationRequestService;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String USERNAME = "PeterG";

    private static final String PASSWORD = "Passw0rdOk1";

    private static final String EMAIL = "peterg@example.test";

    private AuthServiceImpl authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private ConfirmationRequestService confirmationRequestService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository, passwordEncoder, new TransactionTemplate(transactionManager), confirmationRequestService);
    }

    @Nested
    class Register {

        @Test
        void should_save_a_disabled_user_with_a_hashed_password_and_a_lowercased_email() {
            stubSaveEchoingTheUser();

            authService.register(request("PeterG@Example.TEST", USERNAME));

            User saved = savedUser();
            assertThat(saved.getEmail()).isEqualTo(EMAIL);
            assertThat(saved.getUsername()).isEqualTo(USERNAME);
            assertThat(saved.getPassword()).isEqualTo("hashed-password");
            assertThat(saved.isEnabled()).isFalse();
        }

        @Test
        void should_return_the_saved_user_and_request_its_confirmation() {
            stubSaveEchoingTheUser();

            User result = authService.register(request(EMAIL, USERNAME));

            assertThat(result).isSameAs(savedUser());
            verify(confirmationRequestService).requestConfirmation(result);
        }

        @Test
        void should_save_the_user_before_requesting_its_confirmation() {
            stubSaveEchoingTheUser();

            User result = authService.register(request(EMAIL, USERNAME));

            InOrder order = inOrder(userRepository, confirmationRequestService);
            order.verify(userRepository).saveAndFlush(result);
            order.verify(confirmationRequestService).requestConfirmation(result);
        }

        @Test
        void should_hash_the_password_before_the_transaction_starts() {
            stubSaveEchoingTheUser();

            authService.register(request(EMAIL, USERNAME));

            InOrder order = inOrder(passwordEncoder, transactionManager);
            order.verify(passwordEncoder).encode(PASSWORD);
            order.verify(transactionManager).getTransaction(any());
        }

        @Test
        void should_not_open_a_transaction_when_a_pre_check_fails() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request(EMAIL, USERNAME)))
                    .isInstanceOf(DuplicateEmailException.class);

            verify(transactionManager, never()).getTransaction(any());
        }

        @Test
        void should_throw_duplicate_email_when_the_email_is_taken() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request(EMAIL, USERNAME)))
                    .isInstanceOf(DuplicateEmailException.class);
        }

        @Test
        void should_throw_duplicate_username_when_the_lowercased_username_is_taken() {
            when(userRepository.existsByUsernameNormalized("peterg")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request(EMAIL, USERNAME)))
                    .isInstanceOf(DuplicateUsernameException.class);
        }

        @Test
        void should_check_the_email_before_the_username() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request(EMAIL, USERNAME)))
                    .isInstanceOf(DuplicateEmailException.class);

            verify(userRepository, never()).existsByUsernameNormalized(any());
        }

        @Test
        void should_neither_hash_nor_save_nor_request_a_confirmation_when_a_pre_check_fails() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request(EMAIL, USERNAME)))
                    .isInstanceOf(DuplicateEmailException.class);

            verify(passwordEncoder, never()).encode(any());
            verify(userRepository, never()).saveAndFlush(any());
            verify(confirmationRequestService, never()).requestConfirmation(any());
        }

        @Test
        void should_map_a_lost_email_race_to_duplicate_email_by_constraint_name() {
            when(userRepository.saveAndFlush(any(User.class))).thenThrow(violationOf(User.EMAIL_CONSTRAINT));

            assertThatThrownBy(() -> authService.register(request(EMAIL, USERNAME)))
                    .isInstanceOf(DuplicateEmailException.class);
        }

        @Test
        void should_map_a_lost_username_race_to_duplicate_username_by_constraint_name() {
            when(userRepository.saveAndFlush(any(User.class))).thenThrow(violationOf(User.USERNAME_CONSTRAINT));

            assertThatThrownBy(() -> authService.register(request(EMAIL, USERNAME)))
                    .isInstanceOf(DuplicateUsernameException.class);
        }

        @Test
        void should_rethrow_a_violation_of_an_unknown_constraint() {
            DataIntegrityViolationException violation = violationOf("uk_something_else");
            when(userRepository.saveAndFlush(any(User.class))).thenThrow(violation);

            assertThatThrownBy(() -> authService.register(request(EMAIL, USERNAME))).isSameAs(violation);
        }

        @Test
        void should_not_request_a_confirmation_when_the_save_fails() {
            when(userRepository.saveAndFlush(any(User.class))).thenThrow(violationOf(User.EMAIL_CONSTRAINT));

            assertThatThrownBy(() -> authService.register(request(EMAIL, USERNAME)))
                    .isInstanceOf(DuplicateEmailException.class);

            verify(confirmationRequestService, never()).requestConfirmation(any());
        }

        private void stubSaveEchoingTheUser() {
            when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed-password");
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }

        private User savedUser() {
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).saveAndFlush(captor.capture());
            return captor.getValue();
        }

        private DataIntegrityViolationException violationOf(String constraintName) {
            return new DataIntegrityViolationException(
                    "could not execute statement",
                    new ConstraintViolationException("duplicate key", new SQLException(), constraintName)
            );
        }
    }

    @Nested
    class Login {

        private static final String STORED_HASH = "stored-hash";

        @Test
        void should_return_the_user_when_logging_in_by_lowercased_email() {
            User user = confirmedUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);

            User result = authService.login(loginRequest("PeterG@Example.TEST"));

            assertThat(result).isSameAs(user);
            verify(userRepository, never()).findByUsernameNormalized(any());
        }

        @Test
        void should_look_the_username_up_normalized_when_the_identifier_has_no_at_sign() {
            User user = confirmedUser();
            when(userRepository.findByUsernameNormalized("peterg")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);

            User result = authService.login(loginRequest("PeterG"));

            assertThat(result).isSameAs(user);
            verify(userRepository, never()).findByEmail(any());
        }

        @Test
        void should_reject_a_wrong_password() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(confirmedUser()));
            when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(false);

            assertThatThrownBy(() -> authService.login(loginRequest(EMAIL)))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        void should_hash_the_password_anyway_and_reject_when_the_identifier_is_unknown() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(loginRequest(EMAIL)))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(passwordEncoder).encode(PASSWORD);
            verify(passwordEncoder, never()).matches(any(), any());
        }

        @Test
        void should_reject_an_unconfirmed_user_when_the_password_is_correct() {
            User user = confirmedUser();
            user.setEnabled(false);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);

            assertThatThrownBy(() -> authService.login(loginRequest(EMAIL)))
                    .isInstanceOf(EmailNotConfirmedException.class);
        }

        @Test
        void should_report_invalid_credentials_not_unconfirmed_when_the_password_is_wrong() {
            User user = confirmedUser();
            user.setEnabled(false);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(false);

            assertThatThrownBy(() -> authService.login(loginRequest(EMAIL)))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        private User confirmedUser() {
            return User.builder()
                    .email(EMAIL)
                    .username(USERNAME)
                    .password(STORED_HASH)
                    .enabled(true)
                    .build();
        }

        private LoginRequestDTO loginRequest(String identifier) {
            return LoginRequestDTO.builder()
                    .identifier(identifier)
                    .password(PASSWORD)
                    .build();
        }
    }

    private RegisterRequestDTO request(String email, String username) {
        return RegisterRequestDTO.builder()
                .email(email)
                .username(username)
                .password(PASSWORD)
                .build();
    }
}
