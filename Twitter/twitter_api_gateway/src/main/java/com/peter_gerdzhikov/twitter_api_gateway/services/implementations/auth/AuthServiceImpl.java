package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.auth;

import java.util.Locale;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.LoginRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.RegisterRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth.DuplicateEmailException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth.DuplicateUsernameException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth.EmailNotConfirmedException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth.InvalidCredentialsException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth.AuthService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth.ConfirmationRequestService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final TransactionTemplate transactionTemplate;

    private final ConfirmationRequestService confirmationRequestService;

    /**
     * Hashes outside the transaction: bcrypt is slow on purpose, and inside it each registration would hold a
     * database connection for the whole hash.
     */
    @Override
    public User register(RegisterRequestDTO request) {
        String email = request.getEmail().toLowerCase(Locale.ROOT);
        String username = request.getUsername();
        rejectTakenEmailOrUsername(email, username);

        String passwordHash = passwordEncoder.encode(request.getPassword());

        return transactionTemplate.execute(status -> saveAndRequestConfirmation(email, username, passwordHash));
    }

    @Override
    public User login(LoginRequestDTO request) {
        User user = findByIdentifier(request.getIdentifier())
                .orElseThrow(() -> unknownIdentifier(request.getPassword()));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        if (!user.isEnabled()) {
            throw new EmailNotConfirmedException();
        }

        return user;
    }

    private Optional<User> findByIdentifier(String identifier) {
        String normalized = identifier.toLowerCase(Locale.ROOT);

        if (identifier.contains("@")) {
            return userRepository.findByEmail(normalized);
        }

        return userRepository.findByUsernameNormalized(normalized);
    }

    /**
     * Burns one bcrypt hash, so an unknown identifier takes as long as a wrong password and the response
     * time doesn't reveal which accounts exist.
     */
    private InvalidCredentialsException unknownIdentifier(String password) {
        passwordEncoder.encode(password);

        return new InvalidCredentialsException();
    }

    private User saveAndRequestConfirmation(String email, String username, String passwordHash) {
        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordHash)
                .build();
        User saved = saveUser(user);
        confirmationRequestService.requestConfirmation(saved);

        log.info("Registered new user '{}'.", saved.getId());
        return saved;
    }

    private void rejectTakenEmailOrUsername(String email, String username) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }

        if (userRepository.existsByUsernameNormalized(username.toLowerCase(Locale.ROOT))) {
            throw new DuplicateUsernameException();
        }
    }

    /**
     * Flushes inside the try so a concurrent registration that won the race surfaces here, where the
     * constraint name says which of the two fields collided.
     */
    private User saveUser(User user) {
        try {
            return userRepository.saveAndFlush(user);

        } catch (DataIntegrityViolationException e) {
            throw duplicateFor(e);
        }
    }

    private RuntimeException duplicateFor(DataIntegrityViolationException ex) {
        String constraintName = constraintNameOf(ex);
        if (User.EMAIL_CONSTRAINT.equals(constraintName)) {
            return new DuplicateEmailException();
        }

        if (User.USERNAME_CONSTRAINT.equals(constraintName)) {
            return new DuplicateUsernameException();
        }

        return ex;
    }

    private String constraintNameOf(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }

        return null;
    }
}
