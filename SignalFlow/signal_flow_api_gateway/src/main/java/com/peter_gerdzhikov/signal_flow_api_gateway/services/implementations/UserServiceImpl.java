package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.User;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.enums.Role;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.users.UserNotDeletableException;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.users.UserNotFoundException;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.NotificationOutboxRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final NotificationOutboxRepository notificationOutboxRepository;

    @Override
    @Transactional
    public void deleteByEmail(String email, UUID adminId) {
        User user = userRepository
                .findByEmail(email.toLowerCase(Locale.ROOT))
                .orElseThrow(UserNotFoundException::new);

        if (user.getRole() == Role.ADMIN) {
            throw new UserNotDeletableException();
        }

        // An outbox row has no foreign key to users, so it would keep the address and the AI text.
        notificationOutboxRepository.deleteAllByUserId(user.getId());
        userRepository.delete(user);
        log.info("Admin '{}' deleted user '{}'.", adminId, user.getId());
    }
}
