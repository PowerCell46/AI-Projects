package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.User;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.enums.Role;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.users.UserNotDeletableException;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.users.UserNotFoundException;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String EMAIL = "someone@example.com";

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository);
    }

    @Test
    void should_delete_a_user_account() {
        User user = newUser(Role.USER);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        userService.deleteByEmail(EMAIL, ADMIN_ID);

        verify(userRepository).delete(user);
    }

    @Test
    void should_look_the_account_up_by_the_lowercased_email() {
        User user = newUser(Role.USER);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        userService.deleteByEmail("Someone@Example.COM", ADMIN_ID);

        verify(userRepository).delete(user);
    }

    @Test
    void should_refuse_an_admin_account_before_deleting_anything() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(newUser(Role.ADMIN)));

        assertThatThrownBy(() -> userService.deleteByEmail(EMAIL, ADMIN_ID))
                .isInstanceOf(UserNotDeletableException.class);

        verify(userRepository, never()).delete(any());
    }

    @Test
    void should_throw_when_no_account_has_the_email() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteByEmail(EMAIL, ADMIN_ID))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).delete(any());
    }

    private User newUser(Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(EMAIL);
        user.setRole(role);
        return user;
    }
}
