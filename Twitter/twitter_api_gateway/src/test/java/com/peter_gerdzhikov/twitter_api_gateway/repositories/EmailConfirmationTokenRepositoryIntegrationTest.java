package com.peter_gerdzhikov.twitter_api_gateway.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import com.peter_gerdzhikov.twitter_api_gateway.entities.EmailConfirmationToken;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractPostgresIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmailConfirmationTokenRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailConfirmationTokenRepository tokenRepository;

    @Nested
    class FindByTokenHash {

        @Test
        void should_find_a_token_by_its_hash() {
            EmailConfirmationToken saved = savedToken(savedUser(), "hash-find", ISSUED_AT);

            assertThat(tokenRepository.findByTokenHash("hash-find")).contains(saved);
        }

        @Test
        void should_return_empty_for_an_unknown_hash() {
            assertThat(tokenRepository.findByTokenHash("hash-unknown")).isEmpty();
        }

        @Test
        void should_reject_a_duplicate_hash() {
            savedToken(savedUser(), "hash-duplicate", ISSUED_AT);

            assertThatThrownBy(() -> savedToken(savedUser(), "hash-duplicate", ISSUED_AT))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_email_confirmation_tokens_token_hash");
        }
    }

    @Nested
    class FindFirstByUserIdOrderByIssuedAtDesc {

        @Test
        void should_return_the_newest_token_of_the_user() {
            User user = savedUser();
            savedToken(user, "hash-older", ISSUED_AT);
            EmailConfirmationToken newest = savedToken(user, "hash-newest", ISSUED_AT.plusSeconds(90));
            savedToken(user, "hash-oldest", ISSUED_AT.minusSeconds(90));

            assertThat(tokenRepository.findFirstByUserIdOrderByIssuedAtDesc(user.getId())).contains(newest);
        }

        @Test
        void should_ignore_the_tokens_of_other_users() {
            User user = savedUser();
            savedToken(savedUser(), "hash-other-user", ISSUED_AT.plusSeconds(90));

            assertThat(tokenRepository.findFirstByUserIdOrderByIssuedAtDesc(user.getId())).isEmpty();
        }
    }

    @Nested
    class DeleteByUserId {

        @Test
        void should_delete_every_token_of_the_user_and_return_the_count() {
            User user = savedUser();
            savedToken(user, "hash-first", ISSUED_AT);
            savedToken(user, "hash-second", ISSUED_AT.plusSeconds(1));

            int deleted = tokenRepository.deleteByUserId(user.getId());

            assertThat(deleted).isEqualTo(2);
            assertThat(tokenRepository.findFirstByUserIdOrderByIssuedAtDesc(user.getId())).isEmpty();
        }

        @Test
        void should_keep_the_tokens_of_other_users() {
            User user = savedUser();
            User other = savedUser();
            savedToken(user, "hash-mine", ISSUED_AT);
            EmailConfirmationToken kept = savedToken(other, "hash-theirs", ISSUED_AT);

            tokenRepository.deleteByUserId(user.getId());

            assertThat(tokenRepository.findById(kept.getId())).isPresent();
        }
    }

    @Nested
    class CascadeOnUserDelete {

        @Test
        void should_delete_the_tokens_when_the_user_is_deleted() {
            User user = savedUser();
            EmailConfirmationToken token = savedToken(user, "hash-cascade", ISSUED_AT);

            userRepository.deleteById(user.getId());
            userRepository.flush();
            entityManager.clear();

            assertThat(tokenRepository.findById(token.getId())).isEmpty();
        }
    }

    private User savedUser() {
        return userRepository.saveAndFlush(TestEntities.newUser());
    }

    private EmailConfirmationToken savedToken(User user, String tokenHash, Instant issuedAt) {
        EmailConfirmationToken token = EmailConfirmationToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(86_400))
                .build();

        return tokenRepository.saveAndFlush(token);
    }
}
