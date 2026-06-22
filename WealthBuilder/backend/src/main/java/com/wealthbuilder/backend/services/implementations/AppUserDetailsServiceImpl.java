package com.wealthbuilder.backend.services.implementations;

import com.wealthbuilder.backend.config.AppUserDetails;
import com.wealthbuilder.backend.entities.User;
import com.wealthbuilder.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * Bridges our {@link User} entity to Spring Security's {@link UserDetailsService}. Loading
 * per request keeps authorities fresh, so a role change or deletion takes effect on the
 * next call even though sessions are stateless.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppUserDetailsServiceImpl implements UserDetailsService {

    private static final String ROLE_PREFIX = "ROLE_";

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        log.debug("Loading user details for '{}'.", username);

        final User user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));

        return new AppUserDetails(
                user.getUsername(),
                user.getPasswordHash(),
                user.getTokenVersion(),
                List.of(new SimpleGrantedAuthority(ROLE_PREFIX + user.getRole().name())));
    }
}
