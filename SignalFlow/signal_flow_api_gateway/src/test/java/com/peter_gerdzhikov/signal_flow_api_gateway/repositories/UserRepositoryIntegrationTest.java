package com.peter_gerdzhikov.signal_flow_api_gateway.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.User;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.enums.Role;
import com.peter_gerdzhikov.signal_flow_api_gateway.support.AbstractPostgresIntegrationTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMAIL = "bob@example.com";
    private static final String PASSWORD = "hashed-password";

    @Autowired
    private UserRepository userRepository;

    @Test
    void should_save_and_find_a_user_by_email() {
        userRepository.save(newUser(EMAIL));

        Optional<User> found = userRepository.findByEmail(EMAIL);

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void should_lowercase_the_email_before_persisting() {
        User saved = userRepository.save(newUser("Bob@Example.com"));

        assertThat(saved.getEmail()).isEqualTo(EMAIL);
        assertThat(userRepository.findByEmail(EMAIL)).isPresent();
    }

    @Test
    void should_reject_a_duplicate_email() {
        userRepository.saveAndFlush(newUser(EMAIL));

        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser(EMAIL)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User newUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(PASSWORD);
        user.setRole(Role.USER);
        return user;
    }
}
