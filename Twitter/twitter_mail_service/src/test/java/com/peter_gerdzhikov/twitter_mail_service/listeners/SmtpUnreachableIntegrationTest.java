package com.peter_gerdzhikov.twitter_mail_service.listeners;

import java.time.Duration;

import jakarta.mail.internet.MimeMessage;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserConfirmationRequestedEventDTO;
import com.peter_gerdzhikov.twitter_mail_service.support.AbstractKafkaE2ETestSupport;
import com.peter_gerdzhikov.twitter_mail_service.support.MutableClock;
import com.peter_gerdzhikov.twitter_mail_service.support.TestClockConfiguration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Its own Spring context - a different topic, group and (broken) SMTP port from every other e2e test, so it
 * never shares a consumer group with the main suite, and its own {@code spring.mail.port} override isn't
 * shadowed by the shared base's Mailpit {@code @DynamicPropertySource} (which takes precedence over
 * {@code @TestPropertySource} - avoided here by not extending that base at all).
 */
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "app.kafka.user-confirmation-requested.name=smtp-unreachable-test.user.confirmation-requested",
        "app.kafka.user-confirmation-requested.dlt-name=smtp-unreachable-test.user.confirmation-requested-dlt",
        "app.kafka.user-followed.name=smtp-unreachable-test.user.followed",
        "app.kafka.user-followed.dlt-name=smtp-unreachable-test.user.followed-dlt",
        "spring.kafka.consumer.group-id=smtp-unreachable-test-group",
        "spring.mail.host=localhost",
        "spring.mail.port=1",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false"
})
class SmtpUnreachableIntegrationTest extends AbstractKafkaE2ETestSupport {

    private static final String TOPIC = "smtp-unreachable-test.user.confirmation-requested";

    private static final String DLT_TOPIC = "smtp-unreachable-test.user.confirmation-requested-dlt";

    @Autowired
    private MutableClock mutableClock;

    @MockitoSpyBean
    private JavaMailSender javaMailSender;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void should_dead_letter_and_release_the_claim_after_exhausting_retries_when_smtp_is_unreachable() {
        UserConfirmationRequestedEventDTO event = aValidEvent(uniqueRecipient(), mutableClock.instant().plus(Duration.ofHours(24)));
        String key = event.getUserId().toString();

        publish(TOPIC, key, toJson(event));

        awaitDltRecordForKey(DLT_TOPIC, key);
        // The test profile retries 3 times: 1 initial attempt + 3 retries = 4 sends.
        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> verify(javaMailSender, times(4)).send(any(MimeMessage.class)));
        assertFalse(redisTemplate.hasKey(confirmationRedisKey(event.getEventId())));
    }
}
