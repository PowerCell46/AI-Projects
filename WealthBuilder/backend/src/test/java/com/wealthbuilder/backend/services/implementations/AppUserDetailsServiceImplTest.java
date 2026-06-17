package com.wealthbuilder.backend.services.implementations;

import com.wealthbuilder.backend.entities.Role;
import com.wealthbuilder.backend.entities.User;
import com.wealthbuilder.backend.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;


/**
 * Unit test for the bridge between our {@link User} entity and Spring Security's
 * {@code UserDetailsService}. The repository is mocked.
 */
@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceImplTest {

    private static final String USERNAME = "carol";

    private static final String PASSWORD_HASH = "$2a$10$bcrypthashvalue";

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AppUserDetailsServiceImpl userDetailsService;

    @Test
    void should_MapAuthorityAndPassword_When_UserExists() {
        given(userRepository.findByUsername(USERNAME))
                .willReturn(Optional.of(new User(USERNAME, PASSWORD_HASH, Role.MODERATOR)));

        final UserDetails details = userDetailsService.loadUserByUsername(USERNAME);

        assertThat(details.getUsername()).isEqualTo(USERNAME);
        assertThat(details.getPassword()).isEqualTo(PASSWORD_HASH);
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_MODERATOR");
    }

    @Test
    void should_ThrowNotFound_When_UserMissing() {
        given(userRepository.findByUsername(USERNAME)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(USERNAME))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining(USERNAME);
    }
}
