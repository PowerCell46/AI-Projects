package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.locks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.locks.AdvisoryLockKey;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractPostgresIntegrationTest;

@DataJpaTest
@Import(AdvisoryLockServiceImpl.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdvisoryLockServiceImplIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final AdvisoryLockKey KEY = AdvisoryLockKey.UNCONFIRMED_USER_CLEANUP;

    @Autowired
    private AdvisoryLockServiceImpl lockService;

    @Test
    void should_return_the_action_result_when_the_lock_is_free() {
        Optional<String> result = lockService.runIfUnlocked(KEY, () -> "done");

        assertThat(result).contains("done");
    }

    @Test
    void should_not_run_the_action_when_another_connection_holds_the_lock() {
        AtomicBoolean innerRan = new AtomicBoolean(false);

        // Each call takes its own pooled connection, so the nested call is a second session, as another instance would be
        Optional<Optional<String>> outer = lockService.runIfUnlocked(KEY, () -> lockService.runIfUnlocked(KEY, () -> {
            innerRan.set(true);

            return "inner";
        }));

        assertThat(outer).contains(Optional.empty());
        assertThat(innerRan).isFalse();
    }

    @Test
    void should_not_block_a_different_key_while_one_is_held() {
        Optional<Optional<String>> outer = lockService.runIfUnlocked(KEY, () -> lockService.runIfUnlocked(
                AdvisoryLockKey.DATA_SEED,
                () -> "other"
        ));

        assertThat(outer).contains(Optional.of("other"));
    }

    @Test
    void should_release_the_lock_when_the_action_ends() {
        lockService.runIfUnlocked(KEY, () -> "first");

        Optional<String> second = lockService.runIfUnlocked(KEY, () -> "second");

        assertThat(second).contains("second");
    }

    @Test
    void should_release_the_lock_when_the_action_throws() {
        assertThrows(IllegalStateException.class, () -> lockService.runIfUnlocked(KEY, () -> {
            throw new IllegalStateException("boom");
        }));

        Optional<String> after = lockService.runIfUnlocked(KEY, () -> "after");

        assertThat(after).contains("after");
    }
}
