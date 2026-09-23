package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.LoginRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.RegisterRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.User;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.enums.Role;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.auth.DuplicateEmailException;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.auth.InvalidCredentialsException;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.AuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    @Override
    public User register(RegisterRequestDTO request) {
        String email = request.getEmail().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);

        try {
            User savedUser = userRepository.save(user);
            log.info("Registered new user '{}'.", savedUser.getId());
            return savedUser;

        } catch (DataIntegrityViolationException e) {
            // The unique email constraint - a concurrent registration won the race.
            throw new DuplicateEmailException();
        }
    }

    @Override
    public User login(LoginRequestDTO request) {
        String email = request.getEmail().toLowerCase();
        Optional<User> maybeUser = userRepository.findByEmail(email);

        if (maybeUser.isEmpty()) {
            // Burn the same bcrypt cost as a real match, so an unknown email isn't faster than a wrong password.
            passwordEncoder.encode(request.getPassword());
            throw new InvalidCredentialsException();
        }

        User user = maybeUser.get();
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword()) || !user.isEnabled()) {
            throw new InvalidCredentialsException();
        }

        log.info("Logged in user '{}'.", user.getId());
        return user;
    }

    @Override
    public void ensureAdminExists(String email, String password) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }

        User admin = new User();
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);

        User savedAdmin = userRepository.save(admin);
        log.info("Seeded the initial admin user '{}'.", savedAdmin.getId());
    }
}
